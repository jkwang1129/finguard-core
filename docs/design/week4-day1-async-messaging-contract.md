# Week 4 Day 1：异步导入与对账消息契约设计

## 1. 状态与设计结论

- **状态**：已完成
- **本日类型**：设计与评审日，不实现 RabbitMQ 代码、消费者、Flyway 迁移或运行时配置。
- **当前基线**：Week 3 已完成同步 CSV 导入和同步自动对账；完整测试为 `230/230`，真实 MySQL、JWT、HTTP 和失败恢复验收已通过。
- **本日结论**：Week 4 采用“持久化任务意图 + Publisher Confirm + 至少一次投递 + 消费者手动 ACK + 消费幂等 + 重试/DLQ”的异步方案。
- **重要边界**：不追求 exactly-once。消息可能被重复投递，但重复消费不能重复写入交易或对账结果。

本设计承接以下已完成契约，不重新定义 CSV 字段、文件哈希、行级校验、交易唯一键和对账规则：

- [`docs/design/week3-day1-csv-import-contract.md`](week3-day1-csv-import-contract.md)
- [`docs/design/week3-day5-sync-import-orchestration-design.md`](week3-day5-sync-import-orchestration-design.md)
- [`docs/design/week3-day6-sync-reconciliation-design.md`](week3-day6-sync-reconciliation-design.md)
- [`docs/review/week3-review.md`](../review/week3-review.md)

## 2. 业务目标与范围

Week 3 的同步流程已经能完成以下闭环：

```text
上传 CSV
  → 文件请求检查和原始字节 SHA-256
  → 创建 PENDING 导入任务
  → PROCESSING
  → 解析、逐行校验、交易和行错误入库
  → SUCCESS / PARTIAL_SUCCESS / FAILED

触发对账
  → 创建 PENDING 对账任务
  → PROCESSING
  → 两阶段候选匹配
  → 写入四类对账结果
  → COMPLETED / FAILED
```

Week 4 只改变“谁执行、何时执行”的方式：HTTP 请求负责接受任务，RabbitMQ 消费者负责执行任务。业务规则、数据库真源、任务查询接口、权限边界和失败恢复原则继续沿用 Week 3。

### 2.1 本日包含

- 异步导入和异步对账的端到端请求流；
- `POST` 接口从同步终态响应改为受理任务并返回可查询位置的设计；
- 导入消息、对账消息的字段、版本和幂等键；
- exchange、queue、routing key、重试队列和死信队列的拓扑；
- 原始 CSV 在 HTTP 请求结束后的持久化方案；
- 数据库任务、Outbox 和 RabbitMQ 之间的事务边界；
- Publisher Confirm、消费者手动 ACK、重复消费、重试和死信的责任划分；
- Day 2～Day 7 的实现顺序、验收矩阵和回滚边界。

### 2.2 本日不包含

- 不添加 `spring-boot-starter-amqp`、`@RabbitListener` 或 RabbitMQ Docker 服务；
- 不创建或执行 V7/V8 Flyway 迁移；
- 不修改已有 V1～V6；
- 不改写 CSV 解析、字段校验、交易唯一键和对账匹配规则；
- 不引入 Redis、风险规则、人工审核、乐观锁、审计、监控、前端或 MinIO；
- 不提供强制重跑、覆盖导入、手工忽略唯一键或 exactly-once 语义。

## 3. 当前同步基线与异步目标

### 3.1 当前实现的关键事实

Week 3 当前上传接口的核心语义是：

```text
POST /api/import-jobs
  → ADMIN multipart 上传 file
  → 请求级检查和原始字节哈希
  → 相同哈希返回已有任务
  → 新任务进入 PENDING
  → 当前请求内推进 PROCESSING
  → 当前请求内完成解析、校验和入库
  → 返回 201 和终态任务
```

当前 CSV 文件只在请求内存中处理，不落本地磁盘，也没有可供异步消费者读取的原始文件存储。异步化不能简单地把现有 Service 放进 `@Async` 或直接发送一个任务 ID，否则请求结束后消费者没有可靠的文件来源。

当前对账接口保持独立手动触发：

```text
POST /api/reconciliation-jobs
  → 校验导入任务可对账
  → 创建或复用对账任务
  → 当前请求内完成对账
  → 返回 201 或重复请求的 200
```

Week 4 不把“上传完成”和“自动触发对账”强行绑定。对账仍由现有接口触发，只把执行过程异步化。

### 3.2 Week 4 目标导入流程

```text
ADMIN 上传 file
  → 请求级检查、读取原始字节、计算 SHA-256
  → 命中已有 fileHash：返回已有任务，不重新入队
  → 未命中：在一个数据库事务内保存 PENDING 任务、原始文件和 Outbox 事件
  → 返回 202 Accepted + Location=/api/import-jobs/{id}
  → Outbox Relay 读取未发送事件并发布 import.requested
  → Publisher Confirm 成功后标记 Outbox 已发送
  → 导入消费者收到 jobId
  → 条件式 PENDING → PROCESSING
  → 读取持久化原始文件，复用 Week 3 解析、校验和入库逻辑
  → 数据库事务提交
  → 消费者手动 ACK
  → GET 任务详情看到 SUCCESS / PARTIAL_SUCCESS / FAILED
```

### 3.3 Week 4 目标对账流程

```text
ADMIN 提交 importJobId
  → 校验导入任务状态和成功交易数
  → 创建或复用 PENDING 对账任务
  → 在同一数据库事务内保存 Outbox 事件
  → 返回 202 Accepted + Location=/api/reconciliation-jobs/{id}
  → Outbox Relay 发布 reconciliation.requested
  → Publisher Confirm 成功后标记 Outbox 已发送
  → 对账消费者收到 reconciliationJobId
  → 条件式 PENDING → PROCESSING
  → 复用 Week 3 两阶段匹配、结果写入和统计守恒逻辑
  → 数据库事务提交
  → 消费者手动 ACK
  → GET 任务详情看到 COMPLETED / FAILED
```

## 4. HTTP 契约设计

以下是 Week 4 的目标契约。Day 2～Day 5 实现前必须以此为准，不在代码中临时改变语义。

### 4.1 异步导入

```http
POST /api/import-jobs
Content-Type: multipart/form-data
Authorization: Bearer <ADMIN JWT>
```

请求级拒绝继续保持 Week 3 语义：

| 场景 | HTTP | 是否创建任务 | 说明 |
|---|---:|---:|---|
| 未认证 | 401 | 否 | 由安全过滤链处理 |
| 非 ADMIN 上传 | 403 | 否 | 不进入导入 Service |
| 缺少 `file`、扩展名错误、空文件 | 400 | 否 | 请求级错误 |
| 文件超过 5 MiB | 413 | 否 | 请求级大小限制 |
| 文件读取或哈希计算失败 | 500 | 否 | 不返回内部细节 |

文件请求检查通过后：

| 场景 | HTTP | 任务状态 | 说明 |
|---|---:|---|---|
| 首次接受文件且 Outbox 事件已持久化 | 202 | `PENDING` | 返回 `Location`，后台异步执行 |
| 相同原始字节已有任务 | 200 | 原任务当前状态 | `duplicateFile=true`，不创建新任务、不重复入队 |
| 数据库事务失败 | 500 | 不留下半套数据 | 任务、文件和 Outbox 一起回滚 |

首次接受的响应示例：

```json
{
  "id": 123,
  "status": "PENDING",
  "duplicateFile": false,
  "message": "Import job accepted"
}
```

响应头：

```http
HTTP/1.1 202 Accepted
Location: /api/import-jobs/123
```

`GET /api/import-jobs/{id}` 和错误分页接口继续允许 `ADMIN`、`REVIEWER` 查询。查询接口不等待消费者完成，也不暴露 RabbitMQ、Outbox、SQL 或堆栈细节。

### 4.2 异步对账

```http
POST /api/reconciliation-jobs
Authorization: Bearer <ADMIN JWT>
Content-Type: application/json
```

| 场景 | HTTP | 任务状态 | 说明 |
|---|---:|---|---|
| 导入任务不可对账 | 400/404 | 不创建新任务 | 沿用 Week 3 的业务边界 |
| 首次接受对账任务 | 202 | `PENDING` | 返回 `Location` |
| 同一导入任务重复或并发触发 | 200 | 原任务当前状态 | 不重复写结果 |
| 数据库事务失败 | 500 | 不留下半套任务 | 任务和 Outbox 一起回滚 |

对账查询继续允许 `ADMIN`、`REVIEWER`，触发仍只允许 `ADMIN`。Week 4 不自动触发对账，不修改原始交易。

### 4.3 状态查询语义

导入任务状态继续为：

```text
PENDING → PROCESSING → SUCCESS
                     → PARTIAL_SUCCESS
                     → FAILED
```

对账任务状态继续为：

```text
PENDING → PROCESSING → COMPLETED
                     → FAILED
```

RabbitMQ 的重试次数不直接扩展业务任务状态。消息正在第几次重试属于消息基础设施元数据；业务任务只有在最终无法恢复并进入死信处理时，才由独立恢复事务进入 `FAILED`，并保存安全的失败摘要。

## 5. 原始文件持久化方案

### 5.1 选择

选择新增独立的 `import_job_files` 表保存原始字节：

```text
import_job_files
  import_job_id BIGINT PRIMARY KEY
  content LONGBLOB NOT NULL
  content_length INT NOT NULL
  created_at DATETIME(3) NOT NULL
```

表通过 `import_job_id` 与 `import_jobs.id` 建立 `ON DELETE RESTRICT` 外键。原始文件仍受当前 5 MiB 请求上限约束，因此适合当前单体训练项目；文件内容不放进 RabbitMQ 消息。

后续实现应新增 Flyway 迁移，不得修改已应用的 V1～V6。具体版本号、索引和约束在 Day 2 的数据库设计评审中确认。

### 5.2 选择理由与限制

优点：

- 请求结束后消费者仍能读取完全相同的原始字节；
- SHA-256 语义保持不变；
- 应用重启和消费者重启不会丢失待处理文件；
- 不依赖本地磁盘路径，不引入 MinIO；
- 适合当前模块化单体和 5 MiB 文件限制。

限制：

- MySQL 会承载文件内容，不适合无限增长的生产文件仓库；
- 未来如果文件规模明显增长，可把存储替换为对象存储，但不能在 Week 4 偷换成 MinIO 扩大范围。

## 6. 消息契约与 RabbitMQ 拓扑

### 6.1 消息公共字段

导入消息：

```json
{
  "messageId": "outbox-1001",
  "eventType": "IMPORT_REQUESTED",
  "aggregateId": 123,
  "schemaVersion": 1,
  "createdAt": "2026-08-01T10:00:00+08:00"
}
```

对账消息：

```json
{
  "messageId": "outbox-2001",
  "eventType": "RECONCILIATION_REQUESTED",
  "aggregateId": 456,
  "schemaVersion": 1,
  "createdAt": "2026-08-01T10:05:00+08:00"
}
```

约束：

- `aggregateId` 分别对应 `import_jobs.id` 或 `reconciliation_jobs.id`；
- `messageId` 来自 Outbox 记录，发布重试时保持不变；
- 消费幂等主键优先使用业务任务 ID，不能只依赖 RabbitMQ 自动生成的投递标签；
- 消息使用持久化投递模式；
- 消息不携带 CSV 字节、JWT、SQL、文件路径或内部异常。

### 6.2 拓扑命名

第一版使用持久化 exchange、queue、binding 和消息：

| 用途 | Exchange | Routing key | 主队列 |
|---|---|---|---|
| 异步导入 | `finguard.import.exchange` | `import.requested` | `finguard.import.queue` |
| 异步对账 | `finguard.reconciliation.exchange` | `reconciliation.requested` | `finguard.reconciliation.queue` |
| 重试投递 | 对应业务 exchange | 对应 retry key | 业务 retry queue |
| 最终失败 | `finguard.dlx` | 原始 routing key | 对应 DLQ |

具体队列包括：

```text
finguard.import.queue
finguard.import.retry.1.queue
finguard.import.retry.2.queue
finguard.import.dlq

finguard.reconciliation.queue
finguard.reconciliation.retry.1.queue
finguard.reconciliation.retry.2.queue
finguard.reconciliation.dlq
```

第一版只设置有限的两级重试，退避时间在 Day 6 实现时通过配置锁定。不能使用无限 `requeue=true`，否则坏消息会造成消费者忙循环。

## 7. 数据库、Outbox 与 RabbitMQ 的事务边界

### 7.1 任务创建事务

导入首次接受时，以下操作必须在同一个 MySQL 事务中完成：

```text
创建 import_jobs(PENDING)
  + 保存 import_job_files 原始字节
  + 创建 Outbox(IMPORT_REQUESTED, NEW)
  → 一起提交或一起回滚
```

对账首次接受时：

```text
创建 reconciliation_jobs(PENDING)
  + 创建 Outbox(RECONCILIATION_REQUESTED, NEW)
  → 一起提交或一起回滚
```

因此 HTTP `202` 表示“任务和可靠的发布意图已经持久化”，不表示业务处理已经完成。

### 7.2 Outbox 发布事务

Outbox Relay 后续负责：

```text
读取 NEW / RETRY Outbox
  → 发布持久化 RabbitMQ 消息
  → 等待 Publisher Confirm
  → Confirm 成功：标记 SENT
  → Confirm 失败或连接异常：增加 attempts，保留 RETRY
```

Outbox 记录必须有稳定的唯一业务事件键，例如：

```text
UNIQUE(event_type, aggregate_id)
```

这保证同一个导入任务或对账任务不会因为并发请求生成多条业务事件。Outbox 不是 exactly-once；它只关闭“数据库已提交但应用进程在发布前崩溃”的主要丢消息窗口，消费者仍必须幂等。

### 7.3 消费事务和手动 ACK

消费者的顺序必须是：

```text
收到消息
  → 读取并条件更新任务状态
  → 在数据库事务内完成业务处理
  → 数据库事务成功提交
  → 手动 ACK
```

以下情况不能 ACK：

- 数据库连接中断；
- 未预期运行时异常；
- 业务事务回滚；
- 任务状态无法安全判断。

以下情况应该 ACK：

- 业务成功完成；
- 业务错误已被转换为 `FAILED` 或 `PARTIAL_SUCCESS`；
- 消息重复且任务已经处于终态；
- 消息对应的任务不存在，但已经通过安全日志记录并确认不会继续处理。

## 8. 幂等、重试与死信决策表

| 场景 | 消费动作 | 业务结果 | 消息动作 |
|---|---|---|---|
| 首次消费，任务为 `PENDING` | 条件更新为 `PROCESSING` 并执行 | 正常终态 | 事务提交后 ACK |
| 并发消费，另一消费者已抢到任务 | 不执行第二次业务逻辑 | 保持唯一结果 | ACK 或短暂重试，不能重复入库 |
| 重复消息，任务已经成功/部分成功/失败 | 直接幂等返回 | 不新增交易和错误 | ACK |
| 行级业务错误 | 按 Week 3 规则聚合 | `SUCCESS` 或 `PARTIAL_SUCCESS` | ACK |
| 文件级业务错误 | 记录安全摘要 | `FAILED` | ACK |
| 临时数据库或网络错误 | 回滚业务事务 | 任务不应被错误标成成功 | NACK，不重新入主队列无限循环 |
| 超过最大重试次数 | 独立事务标记安全失败 | `FAILED` | 投递 DLQ 并 ACK 原消息 |
| 数据库已提交但 ACK 失败 | 消息重新投递 | 任务已是终态 | 重复消费后直接 ACK |

业务唯一键仍然是最终数据防线：

```text
UNIQUE(account_id, source, external_transaction_no)
```

对账结果唯一键仍然保证同一对账任务和外部交易不会产生重复最终结果。MQ 幂等、Service 条件状态更新和数据库唯一约束是三层不同防线，不能互相替代。

## 9. Day 2～Day 7 执行顺序

| Day | 交付 | 主要验收 |
|---|---|---|
| Day 2 | RabbitMQ 依赖、Docker 服务、基础拓扑和连接配置 | 应用可连接 RabbitMQ；exchange、queue、binding 和健康检查存在 |
| Day 3 | 文件持久化、Outbox 表、任务创建和可靠发布 | 任务、原始文件、Outbox 原子提交；Publisher Confirm 成功/失败可观察 |
| Day 4 | 异步导入消费者、手动 ACK和状态推进 | 成功、部分成功、文件失败和系统失败行为正确 |
| Day 5 | 导入消费幂等、并发抢占和恢复 | 重复消息、ACK 丢失、消费者重启不重复写入 |
| Day 6 | 对账消费者、重试队列和死信队列 | 临时错误重试；坏消息达到上限进入 DLQ；业务错误不盲目重试 |
| Day 7 | Week 4 综合验收和周复盘 | 真实 MySQL/JWT/HTTP/RabbitMQ、权限、幂等、重试、DLQ和清理全部通过 |

## 10. Day 1 验收清单

- [x] 已复盘 Week 3 导入和对账同步调用链，并明确异步切入点。
- [x] 已定义导入和对账的 `202 + Location` 受理语义；重复请求继续复用原任务。
- [x] 已保留当前 ADMIN/REVIEWER 权限边界和 GET 任务查询接口。
- [x] 已明确原始 CSV 不能只保存在 HTTP 内存中，并确定独立文件表方案。
- [x] 已确定消息只携带任务 ID、事件类型、版本和稳定消息 ID。
- [x] 已确定持久化 exchange、queue、routing key、两级重试和 DLQ 拓扑。
- [x] 已区分 Publisher Confirm、Consumer ACK、业务幂等和数据库唯一约束。
- [x] 已定义任务创建事务、Outbox 发布事务和消费者业务事务边界。
- [x] 已定义业务错误、临时系统错误、重复消费、ACK 失败和死信的处理方式。
- [x] 已锁定 Day 2～Day 7 顺序，没有提前实现 Week 5～Week 6 内容。
- [x] 已形成后续实现所需的测试矩阵、回滚边界和面试解释要点。

## 11. 学习重点

本日必须理解：

- HTTP `202` 表示接受任务，不表示业务完成；
- Exchange、Queue、Routing Key 和 Binding 的关系；
- Publisher Confirm 是生产者到 Broker 的确认；
- Consumer ACK 是消费者到 Broker 的确认；
- “至少一次投递”为什么必然要求消费幂等；
- 为什么数据库事务和 MQ 发布不能直接声称原子；
- Outbox 如何降低数据库提交与消息发布之间的丢失窗口；
- 业务错误为什么应该 ACK，而临时系统错误应该重试；
- 为什么不能使用无限 `requeue=true`；
- 为什么文件哈希幂等、MQ 消息幂等和交易唯一键是三件不同的事。

本日暂不展开：Redis、风险规则、人工审核、乐观锁、审计、Micrometer、Prometheus、Linux 部署和压测。

## 12. 回滚边界

本日只有文档和任务清单变更。若设计评审否决异步契约，可删除本设计文档并回退 `TASKS.md` 的 Week 4 Day 1 登记，不涉及 Java 代码、数据库数据、Flyway checksum 或 RabbitMQ 环境。

进入 Day 2 后如果需要调整数据库模型，必须通过新的 Flyway 迁移实现，禁止修改 V1～V6；如果需要调整消息字段，必须增加 `schemaVersion` 或兼容字段，不静默改变已经发布的消息语义。

## 13. 提交

```text
docs: design week4 day1 async messaging contract
```
