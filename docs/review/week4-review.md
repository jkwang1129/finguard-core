# Week 4 综合验收与复盘

## 1. 结论

Week 4 Day 7 已完成。Week 4 的异步导入、异步对账、Outbox 可靠发布、提交后手动 ACK、终态幂等、两级延迟重试和独立 DLQ 均完成本日复验；本日没有新增业务功能，也没有修改 V1～V7。

## 2. Day 1～Day 7 交付索引

| Day | 实际交付 | 提交 |
|---|---|---|
| Day 1 | 异步消息契约、事件版本、幂等键、失败边界 | `996c013` |
| Day 2 | RabbitMQ 依赖、Compose 服务、基础 exchange/queue/binding | `7f77980` |
| Day 3 | 原始文件、Outbox、Publisher Confirm/return、发布退避 | `bfd9a25` |
| Day 4 | 异步导入消费者、业务事务、提交后手动 ACK | `e6c6edf` |
| Day 5 | 导入消费幂等、任务行锁、ACK 丢失红投和崩溃恢复 | `be4779b` |
| Day 6 | 异步对账消费者、失败分类、两级重试和 DLQ | `0015610` |
| Day 7 | 综合验收、真实故障演练、清理和周复盘 | 本次提交 |

## 3. 干净环境与自动化测试

- 仅重建 Compose 管理的 `finguard-core_mysql_data`、`finguard-core_rabbitmq_data_v4`；旧的同名历史 RabbitMQ 数据卷未删除。
- MySQL、RabbitMQ 均恢复为 `healthy`；空库自动执行 Flyway V1～V7。
- RabbitMQ 重新声明 3 个 exchange、8 个 durable queue：主队列、5 秒 retry.1、30 秒 retry.2 和独立 DLQ 各覆盖导入/对账两条链路；DLX、routing key、TTL、持久化参数与代码配置一致。
- 聚焦测试共 56/56 通过；完整 `mvn clean test` 为 **287/287**，`Failures=0`、`Errors=0`、`Skipped=0`。

## 4. 真实 Java/JWT/HTTP 链路

- Java 17 应用 `/actuator/health` 返回 HTTP 200、`UP`。
- ADMIN 登录后创建账户和人工交易；首次 CSV 上传返回 `202` 与 `Location`，任务最终 `SUCCESS`，2 行成功；重复上传返回 `200` 并复用原任务。
- 首次对账返回 `202` 与 `Location`，最终 `COMPLETED`，2 条结果中 `matched=1`、`unmatched=1`；重复请求返回 `200` 并复用原任务。
- REVIEWER 查询导入/对账及结果为 `200`，写入/触发操作为 `403`；匿名查询为 `401`；不存在的导入任务为 `404`。

## 5. Outbox 与消费者故障证据

### Outbox/Broker 故障

停止 RabbitMQ 后提交的导入任务保持 `PENDING`；对应 Outbox 事件进入 `RETRY`，`attempts=2`、`last_error=PUBLISH_FAILED`，`sent_at` 为空。恢复 RabbitMQ 后同一稳定 `messageId` 成功发布，事件变为 `SENT`，任务最终 `SUCCESS`，新增业务交易只有 1 条。

### 消费者临时故障

- 导入：破坏原始文件内容后，消息进入 retry.1；修复文件后恢复为 `SUCCESS`，1 行成功。
- 对账：制造带残留结果的 `PROCESSING` 任务后，消息进入 retry.1；修复状态后恢复为 `COMPLETED`，结果数为 2。

### 消费者持续故障、DLQ 与安全终态

- 导入：持续文件校验失败经过 retry.1、retry.2 后进入 `finguard.import.dlq`；取出的消息头为 `attempt=2`、`failure=RETRY_EXHAUSTED`，任务安全标记 `FAILED`。
- 对账：持续残留结果故障真实观察到 retry.2 和 `finguard.reconciliation.dlq`；取出的消息头为 `attempt=2`、`failure=RETRY_EXHAUSTED`，任务安全标记 `FAILED`。retry.1 的延迟转交和队列参数由同一真实临时故障及自动化消费者测试复核。
- 8 个队列均无 ready 或 unacked 消息，主队列没有忙循环。非法消息、非法 attempt、任务不存在、转交失败和 ACK 丢失的重复副作用由聚焦测试覆盖。

## 6. 清理与范围审计

- 按外键顺序清理验收账户、交易、导入任务、原始文件、行错误、对账任务、对账结果和 Outbox；数据库业务表计数均为 0。
- 删除本次临时验收用户 `day7-admin`、`day7-reviewer`；未提交 `.env`、JWT 密钥、数据库/RabbitMQ 密码或内部异常堆栈。
- 清空本项目 8 个 `finguard.*` 队列；停止 Java/Maven 进程后确认 8080 端口已释放。
- `git diff --check` 通过；变更仅限 `TASKS.md`、`README.md` 和本复盘文档，不包含 Week 5 Redis/风险/审核/审计或 Week 6 部署/监控内容。

## 7. 面试复盘与限制

这周的可靠性链路是：HTTP `202` → 业务数据与 Outbox 同事务提交 → Publisher Confirm/mandatory return → 持久化消息 → 消费者事务 → 提交后手动 ACK → 终态幂等；临时故障使用有限退避，毒消息进入 DLQ。Outbox 解决“事件不丢”，幂等键、唯一约束、任务行锁和终态短路解决“业务不重复”，但整体仍是至少一次投递，不能宣称端到端 exactly-once。Week 5 再进入 Redis、风险规则和审核能力。
