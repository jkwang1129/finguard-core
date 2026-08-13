# RabbitMQ 两级重试与 DLQ 故障演练

## 性质与边界

- 性质：受控消费者故障注入演练，不是生产事故。
- 日期：2026-08-12 至 2026-08-13（Asia/Shanghai）。
- 环境：固定隔离项目 `finguard-day6`，测试进程连接其 RabbitMQ。
- 扰动：只在测试 Spy 消费者中注入瞬时或持续异常，不修改生产业务代码。

## 影响与现象

第一次执行时，瞬时故障约 1 秒即完成，持续故障任务意外成为 `COMPLETED`，与 5 秒、30 秒两级延迟契约不符。隔离 Compose 应用和测试 Spy 同时监听同一对账队列，常驻消费者可能先抢到消息并绕过故障注入。

暂停隔离 app、确保 Spy 是唯一消费者后，两项测试 2/2 通过。持续故障最终消息头为 `x-finguard-retry-attempt=2`，失败码为 `RETRY_EXHAUSTED`，任务终态为 `FAILED`，没有重复写入对账结果。

## 时间线

1. 启动隔离 RabbitMQ、常驻 app 和测试 Spy，执行瞬时/持续故障用例。
2. 观察 2 个用例失败：时间不符且持续故障被常驻消费者正常完成。
3. 对比监听者和队列，定位为竞争消费者而非重试实现缺陷。
4. 修改演练边界：停止隔离 app，只保留测试 Spy 消费目标队列。
5. 重跑聚焦测试，确认瞬时故障在有限重试后成功，持续故障进入 DLQ。
6. 在 `finally` 中重新启动 app、等待 health=200，并清空相关测试队列与数据库行。

## 根因

根因是故障注入环境存在两个竞争消费者。异常只存在于 Spy；常驻应用拿到消息时会走正常路径，因此首次现象无法证明重试实现错误。确保单一受控消费者后，原两级重试/DLQ 契约得到验证。

消息系统仍采用至少一次语义；手动 ACK、任务状态和数据库唯一约束共同抵抗重复投递，本项目不声称 Exactly Once。

## 恢复与验证

恢复时重新启动隔离 app 并轮询 health=200。验证同时覆盖重试时间、终态、DLQ 头、失败码、对账结果数量、ready/unacked 情况以及测试数据/队列清理，而不只查看 Maven 退出码。

## 预防

- 消费者故障注入必须确保目标队列只有受控消费者。
- 演练脚本必须在 `finally` 恢复 app 并等待健康。
- 重试验证同时断言延迟、消息头、任务终态、结果幂等和 DLQ。
- DLQ 消息在保留证据前不得批量重放或删除。

## 证据

- [Day 6 故障演练总复盘](../review/week6-day6-fault-drills.md)
- [RabbitMQ 重试/DLQ 演练脚本](../../scripts/drills/invoke-messaging-retry-drill.ps1)
- [MySQL 事实源与 Outbox ADR](../adr/0002-mysql-truth-and-outbox.md)
- [依赖故障响应 Runbook](../runbooks/dependency-incident-response.md)
