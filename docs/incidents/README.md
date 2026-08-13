# FinGuard Core 故障演练记录

本目录采用事故复盘格式记录 2026-08-12 至 2026-08-13 完成的三次受控故障演练。它们发生在固定 `finguard-day6` 隔离环境中，不是真实生产事故，也不证明生产高可用或灾难恢复能力。

## 演练索引

| 依赖 | 注入条件 | 观察结果 | 恢复 | 记录 |
| --- | --- | --- | --- | --- |
| Redis | 停止隔离 Redis | health 503；登录和统计仍为 200 | 启动同一 Redis，health/统计恢复 200 | [Redis 中断](redis-outage.md) |
| RabbitMQ | Spy 消费者注入瞬时/持续失败 | 排除竞争消费者后两级重试与 DLQ 符合契约 | 恢复常驻 app 并轮询健康 | [重试与 DLQ](rabbitmq-retry-dlq.md) |
| MySQL | 一次性 app 使用无效端口 | 故障容器快速 `exited|1`，健康实例不受影响 | 删除一次性容器 | [错误配置](mysql-misconfiguration.md) |

## 阅读方式

每份记录都包含性质与边界、影响与现象、时间线、根因、恢复与验证、预防和证据。需要执行响应时先阅读 [依赖故障响应 Runbook](../runbooks/dependency-incident-response.md)；完整原始结果和清理边界见 [Week 6 Day 6 故障复盘](../review/week6-day6-fault-drills.md)。

## 共同边界

- 所有秘密由进程或临时文件随机生成，记录中不保存具体值。
- 未操作普通开发栈或 Linux 保留部署。
- 三项演练都禁止删除数据卷。
- 演练结束后隔离容器、网络、卷、消息、测试行、临时环境和秘密文件均已清理。
