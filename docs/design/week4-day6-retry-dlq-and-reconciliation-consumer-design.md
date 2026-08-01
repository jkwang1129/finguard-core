# Week 4 Day 6：有限重试、死信隔离与异步对账消费者设计

## 1. 状态与目标

- **状态**：已按本设计完成实现、自动化测试和真实验收。
- **业务目标**：让 `RECONCILIATION_REQUESTED` 真正驱动对账处理，并让导入、对账两条消费链路对临时故障进行有限延迟重试、对坏消息进行可调查隔离。
- **可靠性目标**：不追求 exactly-once；通过稳定消息 ID、数据库任务状态、行锁、业务唯一约束和手动 ACK，在至少一次投递下保持唯一业务结果。
- **实现前基线**：导入消费者已经具备单事务处理、终态幂等、同任务行锁、崩溃回滚和 ACK 丢失红投恢复；对账 Outbox 已能发布到主队列，但尚无对账消费者；两条链路均没有 retry queue 和 DLQ。

本日不实现自动触发对账、人工重跑接口、Redis、风险、审核、审计、监控或前端。

## 2. 消息处理结果与失败分类

### 2.1 四类结果

| 分类 | 例子 | 业务动作 | 消息动作 |
|---|---|---|---|
| 安全完成 | 首次成功、部分成功、文件级失败、终态重复 | 提交或保留确定终态 | ACK |
| 永久消息错误 | 非法字段、错误事件类型/版本、非法 attempt、任务不存在 | 不调用或停止调用业务处理；不伪造任务 | 直接 DLQ，转交确认后 ACK 原消息 |
| 确定性业务失败 | 对账输入在受理后出现不可恢复的一致性破坏 | 独立短事务安全标记真实非终态任务 `FAILED` | ACK；不盲目重试 |
| 临时系统错误 | 短暂数据库连接、事务回滚、未预期运行时错误 | 当前业务事务整体回滚 | retry.1 → retry.2 → 耗尽后安全失败并 DLQ |

未知运行时错误默认按临时系统错误处理，但最多只执行两次延迟重试。这样既不给未知故障直接丢消息，也不会形成无限循环。

### 2.2 不得混淆的边界

- CSV 行错误、重复交易和可记录的文件级失败属于导入业务结果，不属于 MQ 重试条件。
- 数据库已经提交但 ACK 失败时，Broker 红投原消息；终态幂等短路后 ACK，不消耗业务重试次数。
- retry/DLQ 转交发布失败不是“业务第 N 次失败”；原消息保持未确认，等待连接恢复后重新投递。
- `deliveryTag` 只在当前 Channel 有效；`x-death` 是 Broker 诊断元数据；二者都不是业务幂等键。

## 3. 拓扑

### 3.1 固定名称

```text
finguard.import.exchange
  import.requested  -> finguard.import.queue
  import.retry.1    -> finguard.import.retry.1.queue
  import.retry.2    -> finguard.import.retry.2.queue

finguard.reconciliation.exchange
  reconciliation.requested -> finguard.reconciliation.queue
  reconciliation.retry.1   -> finguard.reconciliation.retry.1.queue
  reconciliation.retry.2   -> finguard.reconciliation.retry.2.queue

finguard.dlx
  import.requested         -> finguard.import.dlq
  reconciliation.requested -> finguard.reconciliation.dlq
```

全部 exchange、queue、binding 和重发消息均为 durable/persistent。

### 3.2 TTL 与回流

| 队列 | TTL | 到期后的 DLX | 到期后的 routing key |
|---|---:|---|---|
| `finguard.import.retry.1.queue` | 5 秒 | `finguard.import.exchange` | `import.requested` |
| `finguard.import.retry.2.queue` | 30 秒 | `finguard.import.exchange` | `import.requested` |
| `finguard.reconciliation.retry.1.queue` | 5 秒 | `finguard.reconciliation.exchange` | `reconciliation.requested` |
| `finguard.reconciliation.retry.2.queue` | 30 秒 | `finguard.reconciliation.exchange` | `reconciliation.requested` |

主队列配置 `x-dead-letter-exchange=finguard.dlx`，不覆盖原 routing key。这样消息转换阶段就失败、尚未进入 Listener 的坏消息也能被 Broker 隔离到正确 DLQ。

改变已存在 durable queue 的 arguments 会触发 RabbitMQ `PRECONDITION_FAILED`。本地升级前必须先确认两个主队列 `ready=0/unacked=0`，再只删除并由应用重建本项目明确命名的主队列；禁止删除 RabbitMQ 数据卷或其他队列。

## 4. 重试元数据

使用自定义整数头：

```text
x-finguard-retry-attempt
```

| 收到的头 | 当前含义 | 临时失败后的去向 |
|---:|---|---|
| 缺失或 `0` | 首次消费 | 写为 `1`，发布 retry.1 |
| `1` | 第一次延迟重试 | 写为 `2`，发布 retry.2 |
| `2` | 第二次延迟重试 | 重试耗尽，发布 DLQ |
| 负数、非整数或大于 `2` | 非法元数据 | 直接 DLQ |

重发必须保持原消息的 `messageId`、JSON body、eventType、aggregateId、schemaVersion 和 createdAt，只能增加或更新受控 attempt 头与安全失败分类头。不得把异常消息、堆栈、SQL、JWT、密码、CSV 原文或文件路径写进 header。

## 5. 可靠转交与 ACK 时序

### 5.1 临时故障

```text
主队列投递
  -> 业务事务异常并回滚
  -> 根据 attempt 选择 retry.1 / retry.2
  -> 发布持久化重试消息（稳定 messageId）
  -> 等待 Publisher Confirm
  -> Confirm ACK 且没有 mandatory return
  -> ACK 原消息
  -> retry queue TTL 到期后由 Broker 回流主队列
```

### 5.2 永久消息错误或重试耗尽

```text
确认不应继续业务处理
  -> 若存在真实非终态任务且重试耗尽：独立短事务安全标记 FAILED
  -> 发布原消息到 finguard.dlx
  -> 等待 Confirm ACK 且没有 mandatory return
  -> ACK 原消息
```

必须先确认下一跳发布成功，再 ACK 原消息。若发布 NACK、return、超时或抛异常，Listener 对原 delivery 执行 `basicNack(..., requeue=true)` 或让 Channel 关闭形成红投，不得 ACK。该窗口可能造成重复发布，但不会造成消息丢失；数据库幂等负责兜底重复。

## 6. 对账消费者

### 6.1 消息契约

只接受：

- `messageId` 匹配 `outbox-[1-9][0-9]*`；
- `eventType=RECONCILIATION_REQUESTED`；
- `aggregateId` 为正数且对应真实 `reconciliation_jobs.id`；
- `schemaVersion=1`；
- `createdAt` 非空；
- retry attempt 合法。

消息只携带任务 ID；导入任务、交易和对账规则全部以 MySQL 为真源。

### 6.2 所有权和幂等

对账处理入口在一个数据库事务内执行：

```sql
SELECT ...
FROM reconciliation_jobs
WHERE id = ?
FOR UPDATE;
```

| 锁定后的状态 | 处理结果 | Listener 动作 |
|---|---|---|
| `PENDING` | 推进到 `PROCESSING`，计算并写结果，提交 `COMPLETED` | ACK |
| 可恢复的遗留 `PROCESSING` | 删除不得存在的半套结果前先由测试证明原事务已回滚；重新计算并提交 | ACK |
| `COMPLETED` / `FAILED` | 不重新匹配、不新增结果 | ACK |
| 任务不存在 | 永久消息错误 | DLQ |

同一任务的并发消息在任务行锁上串行。第一条消息提交 `COMPLETED` 后，第二条只读取终态并返回 `ALREADY_COMPLETED`。不同任务仍可并发。

### 6.3 事务边界

以下动作必须在同一事务中完成：

```text
锁定 reconciliation_job
  -> PENDING/遗留 PROCESSING -> PROCESSING
  -> 读取关联导入交易和人工候选
  -> 复用 ReconciliationMatcher
  -> 批量写 reconciliation_results
  -> 校验 total = matched + unmatched + duplicate + suspicious
  -> PROCESSING -> COMPLETED
  -> COMMIT
```

临时异常整体回滚到消费前可见状态，不调用旧的“捕获所有异常后立即 `markProcessingFailed`”路径。只有确定性业务失败或重试耗尽，才使用独立条件更新写入固定安全摘要。条件更新不得覆盖 `COMPLETED` 或已有 `FAILED`。

本日默认不新增 V8：现有 `UNIQUE(reconciliation_job_id, csv_transaction_id)`、任务状态和行锁足以形成三层防线。若实现实验否定这一结论，必须先记录真实 SQL 证据，再新增迁移，不能修改 V6/V7。

## 7. 组件职责

- `RabbitMessagingConfiguration`：声明主 exchange/queue、两级 retry queue、DLX、DLQ 与全部 binding。
- 消费失败路由组件：校验 attempt、选择下一跳、写安全 header、等待 confirm/return，并返回明确发布结果。
- 导入 Listener：调用现有 Handler；安全完成后 ACK；永久消息错误直接隔离；临时错误交给失败路由。
- 对账 Handler：校验对账消息，只把任务 ID 交给事务 Service。
- 对账事务 Service：行锁、状态决策、匹配、结果写入、统计和终态幂等。
- 失败恢复 Service：仅对真实非终态任务执行条件式 `FAILED` 更新；不接受消息体中的任意数据作为更新内容。

## 8. 自动化测试矩阵

### 8.1 拓扑

- 2 个业务 exchange、1 个 DLX、2 个主队列、4 个 retry queue、2 个 DLQ 都存在且 durable；
- retry TTL、DLX 和 routing key 与本设计一致；
- 主 routing key 不会误入 retry/DLQ，未知 routing key 在 mandatory 模式下可检测；
- retry 消息到期后回到正确主队列且 attempt/messageId 保持。

### 8.2 失败路由

- attempt 0 → retry.1，1 → retry.2，2 → DLQ；
- 非法 attempt 直接 DLQ；
- confirm NACK、return、超时或异常时不允许 ACK 原消息；
- 转交成功后才 ACK；模拟原 ACK 失败后重复消费不产生业务副作用。

### 8.3 导入

- Day 5 的成功、部分成功、文件失败、顺序重复、并发重复、ACK 丢失和事务回滚测试全部保留；
- 临时异常一次后经 retry.1 恢复；
- 连续异常耗尽后任务条件式 `FAILED`，消息只进入 import DLQ；
- 非法消息/任务不存在不写交易、行错误、任务或文件。

### 8.4 对账

- 首次消息完成四类结果与统计守恒；
- 终态重复和至少两个并发消费者只产生一套结果；
- 事务异常回滚结果和状态，解除故障后重试成功；
- ACK 丢失红投只做终态短路；
- 连续故障耗尽后安全失败并进入 reconciliation DLQ；
- 非法消息和不存在任务不产生数据库副作用。

## 9. 真实验收

1. 确认 MySQL/RabbitMQ healthy，主/retry/DLQ 初始为空。
2. 启动真实 Java 17 应用并检查 `/actuator/health`。
3. ADMIN 登录，创建账户与人工交易，上传 CSV，轮询导入终态。
4. 创建对账任务，确认 `202/PENDING + Location`，轮询到 `COMPLETED`，核对四类统计和结果行。
5. 注入一次可恢复故障，观察 retry.1 至少延迟 5 秒后回流并成功。
6. 注入持续故障，观察 retry.1、retry.2 和最终 DLQ，确认没有主队列忙循环和半套数据。
7. 发布非法消息，确认只进入正确 DLQ，数据库零副作用。
8. 清理用户、账户、任务、文件、Outbox、交易、对账结果和全部测试消息；确认端口释放。
9. 运行完整 `mvn clean test`、`git diff --check` 和范围审计。

## 10. 实现与验收结论

- 导入与对账均已接入统一失败分类、5 秒/30 秒两级 retry queue、可靠 Publisher Confirm/return 转交和独立 DLQ；重试保留稳定消息体和 `messageId`。
- 对账消费者以任务行为锁，在一个事务内完成状态推进、匹配、结果批量写入、统计守恒和 `COMPLETED`；临时异常整体回滚，终态重复与 ACK 丢失红投只做幂等短路。
- 真实 RabbitMQ 测试验证了两条链路的一次失败后恢复、两次重试耗尽后安全失败和 DLQ、并发重复、任务不存在、非法 attempt 及转交失败不 ACK。
- 完整 `mvn clean test` 为 287/287 通过。真实 Java 17 + JWT + HTTP + MySQL + RabbitMQ 验收完成异步导入与异步对账，数据库结果和 Outbox 数量一致；验收数据、8 个队列和端口均已清理。
- 未新增 V8，V1～V7 未修改；Redis、人工重跑、风险、审核、审计、监控和前端仍在本日范围外。

## 11. 回滚

- 回退 Day 6 新增的 topology、失败路由、对账消费者、对账事务入口、配置和测试即可恢复 Day 5 代码行为。
- V1～V7 和已有业务数据不得删除或重放。
- 若本地 Broker 已声明 Day 6 queue arguments，只能在确认空队列后删除本项目明确命名的 Day 6 队列并重新声明；禁止删除 RabbitMQ 数据卷。
