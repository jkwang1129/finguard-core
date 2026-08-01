# Week 4 Day 3：文件持久化与可靠 Outbox 发布设计

## 1. 目标与范围

- **状态**：已完成。
- **业务目标**：HTTP 只负责可靠受理导入/对账任务；任务意图先与业务数据一起提交到 MySQL，再由 Outbox Relay 投递到 Day 2 已建立的 RabbitMQ 主队列。
- **本日交付**：V7、原始 CSV 持久化、Outbox 持久层、`202 Accepted` 改造、持久化 JSON 消息、Publisher Confirm + mandatory return、发布退避和测试/真实验收。
- **明确不做**：不添加消费者、手动 ACK、消费重试队列或 DLQ；不改变 Week 3 CSV 校验、交易写入和对账规则；不引入 Redis、风险、审核、审计、监控或前端。

## 2. 请求与事务边界

```text
导入请求
  -> 校验请求并计算 SHA-256
  -> 同一 MySQL 事务：PENDING import_job + import_job_file + IMPORT_REQUESTED outbox
  -> 202 + Location

对账请求
  -> 校验可对账的 import_job
  -> 同一 MySQL 事务：PENDING reconciliation_job + RECONCILIATION_REQUESTED outbox
  -> 202 + Location

Outbox Relay
  -> 有限批量查询到期的 NEW/RETRY
  -> 发布持久化消息并等待 correlated confirm
  -> ACK 且无 mandatory return：SENT
  -> NACK/return/超时/异常：RETRY + attempts + next_attempt_at
```

重复文件和重复对账请求继续返回原任务，不写第二份文件、不写第二个 Outbox 事件。数据库唯一键是并发竞态的最终保护。

## 3. V7 数据模型

### 3.1 `import_job_files`

| 字段 | 约束 | 说明 |
|---|---|---|
| `import_job_id` | PK、FK -> `import_jobs.id`、RESTRICT | 与任务一对一 |
| `content` | `LONGBLOB NOT NULL` | 原始 CSV 字节，不放入消息 |
| `content_length` | 1..5 MiB | 与 `OCTET_LENGTH(content)` 一致 |
| `created_at` | 毫秒时间 | 审计创建时间 |

### 3.2 `outbox_events`

| 字段 | 约束 | 说明 |
|---|---|---|
| `id` | 自增主键 | 稳定消息 ID 为 `outbox-{id}` |
| `event_type` | `IMPORT_REQUESTED` / `RECONCILIATION_REQUESTED` | 决定 exchange 和 routing key |
| `aggregate_id` | 正整数 | 对应任务 ID；不建立多态外键 |
| `schema_version` | 固定为 1 | 消息演进边界 |
| `status` | `NEW` / `RETRY` / `SENT` | 发布状态机 |
| `attempts` | 非负整数 | 每次发布失败后加一 |
| `next_attempt_at` | 必填 | Relay 的到期扫描条件 |
| `last_error_summary` | 可空、最多 255 字符 | 只保存稳定错误码，不保存凭据或完整 Broker 异常 |
| `sent_at` | SENT 时必填 | 成功确认时间 |

唯一键 `(event_type, aggregate_id)` 防止同一业务任务重复建事件；索引 `(status, next_attempt_at, id)` 支持稳定有限批量扫描。

## 4. 消息与发布契约

消息只携带任务定位信息：

```json
{
  "messageId": "outbox-123",
  "eventType": "IMPORT_REQUESTED",
  "aggregateId": 456,
  "schemaVersion": 1,
  "createdAt": "2026-08-01T10:00:00+08:00"
}
```

- 导入事件发送到 `finguard.import.exchange` / `import.requested`。
- 对账事件发送到 `finguard.reconciliation.exchange` / `reconciliation.requested`。
- 消息 delivery mode 为 persistent，RabbitTemplate 开启 `mandatory`、correlated confirms 和 publisher returns。
- 只有 Confirm ACK 且没有 returned message 才能写 `SENT`。NACK、return、确认超时和发布异常分别记录稳定错误码。
- 失败退避：第 1 次 5 秒、第 2 次 30 秒、第 3 次及以后 5 分钟；不丢弃 Outbox，后续仍可恢复发布。

## 5. 并发、故障与可恢复性

- 任务、文件和事件任何一步失败都由同一事务整体回滚。
- Relay 采用 at-least-once：应用在 Broker 已接收、但 `SENT` 提交前崩溃时允许重复发布；稳定 `messageId` 供后续消费者幂等。
- Day 3 不声明消息已被业务消费。消息进入 durable 主队列即完成本日发布责任。
- 单实例 Relay 的重复扫描通过短周期和状态更新控制；多实例的行级抢占/租约属于后续扩展，本日不虚构 exactly-once。
- 本日没有消费者，因此新任务保持 `PENDING`，主队列消息由后续 Day 消费；验收消息必须在结束时清理。

## 6. 验收标准

- V7 可从既有 V1-V6 升级，表、外键、唯一键、检查约束和扫描索引均存在。
- 新导入请求返回 `202/PENDING`，原始字节与 Outbox 同事务入库；重复上传不新增记录。
- 新对账请求返回 `202/PENDING`，Outbox 同事务入库；重复触发不新增记录。
- 两类事件均以正确 JSON、exchange、routing key 和 persistent delivery mode 到达真实 durable queue。
- Confirm ACK + 无 return 后事件为 `SENT`；不可路由消息产生 mandatory return；模拟发布失败后状态为 `RETRY` 且退避可观察。
- 聚焦测试与完整 `mvn clean test` 通过；真实 JWT/HTTP/MySQL/RabbitMQ 验收通过；测试数据、队列消息和临时端口清理完成；`git diff --check` 通过。

## 7. 回滚边界

Java 和配置可通过回退本日提交恢复同步入口；V7 一旦应用不得修改或删除，只能用后续迁移演进。回滚前需确认 V7 数据仍被保留，不能通过删除数据卷掩盖迁移问题。
