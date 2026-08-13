# Redis、RabbitMQ、MySQL 依赖故障响应 Runbook

## 目标

在不扩大影响面的前提下定位依赖故障，恢复服务并用业务事实和监控证明恢复。此流程适用于本项目的本地隔离环境和授权 Linux 部署；任何扰动命令只能作用于明确命名的测试资源。

## 1. 统一响应顺序

1. **确认影响**：记录时间、当前 Git SHA/镜像、受影响 API、HTTP 状态、任务 ID 范围和最近变更；不要记录 JWT 或口令。
2. **读取信号**：检查应用 health、Prometheus target/业务指标、Compose 状态和白名单服务的有限日志。
3. **区分依赖**：Redis 关注缓存/限流与回源；RabbitMQ 关注 ready/unacked、重试头和 DLQ；MySQL 关注连接、Flyway 版本和事务事实。
4. **限定处置**：只重启或移除已确认的故障实例；不删除卷，不清空普通队列，不修改防火墙或外部凭据。
5. **恢复验证**：依次验证 health、认证探针、数据库事实、队列、Redis key/TTL、Prometheus target 和相关业务指标。
6. **清理与记录**：删除一次性故障资源和测试数据，保留脱敏时间线、根因、恢复与预防证据。

Linux 状态和有限日志命令：

```sh
sh scripts/linux/status.sh
sh scripts/linux/logs.sh app 100
sh scripts/linux/logs.sh redis 100
sh scripts/linux/logs.sh rabbitmq 100
sh scripts/linux/logs.sh mysql 100
```

## 2. Redis 信号与恢复

预期降级是统计缓存 miss 后回源 MySQL，登录/上传限流 fail-open；应用健康仍应暴露 Redis 异常。确认 MySQL 查询结果正确后恢复 Redis，再验证统计 key 可重新生成、TTL 有界、health 和指标恢复。

若统计结果错误、MySQL 也不可用或限流绕过已产生安全影响，停止普通恢复并升级处理。参考 [Redis 中断演练](../incidents/redis-outage.md)。

## 3. RabbitMQ 信号与恢复

检查任务是否停留在非终态、队列是否存在 ready/unacked、消息是否带 `x-finguard-retry-attempt`、重试是否最终进入 DLQ。验证环境必须确保目标队列只有受控消费者，避免竞争消费者绕过故障注入。

恢复后必须确认任务终态、业务结果数量无重复、主队列无遗留 unacked，并对 DLQ 消息逐条保留证据后再决定重放或终止。无法解释消息重复、DLQ 已耗尽或数据库与任务状态不一致时升级处理。参考 [RabbitMQ 重试/DLQ 演练](../incidents/rabbitmq-retry-dlq.md)。

## 4. MySQL 信号与恢复

应用启动失败、health 不可用、连接拒绝或 Flyway 版本异常时，先核对环境文件、目标端口、数据库容器健康和 schema 版本。配置错误的临时应用应快速失败；移除一次性故障实例后验证原健康实例未被替换。

发现迁移校验失败、schema 版本超出目标应用兼容范围、数据完整性不确定或缺少备份/回滚证据时立即停止，不尝试删除迁移记录或数据卷。参考 [MySQL 错误配置演练](../incidents/mysql-misconfiguration.md)。

## 5. 恢复完成条件

- 应用 health 与 Prometheus target 恢复；
- 最小认证请求返回预期 200，401/403 边界仍正确；
- 任务、交易、审核和审计事实一致；
- RabbitMQ 没有无法解释的 ready/unacked，Redis 缓存可重建；
- 一次性容器、消息、key、数据和临时秘密已按隔离边界清理；
- 形成“现象 → 假设 → 证据 → 根因 → 恢复 → 预防”记录。

监控查询入口见 [监控与排障](../MONITORING.md)，三次源演练见 [Day 6 故障复盘](../review/week6-day6-fault-drills.md)。
