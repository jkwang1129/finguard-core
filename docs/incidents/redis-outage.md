# Redis 中断、降级与恢复故障演练

## 性质与边界

- 性质：受控依赖中断演练，不是生产事故。
- 日期：2026-08-12 至 2026-08-13（Asia/Shanghai）。
- 环境：固定隔离项目 `finguard-day6`。
- 扰动：只停止并重新启动隔离 Redis；不重建数据库、应用或数据卷。

## 影响与现象

中断前应用 health 和统计接口均返回 200。停止 Redis 后，health 因 Redis 健康组件无法连接而返回 503；重新登录和统计接口仍返回 200。统计服务捕获缓存异常并回源 MySQL，登录限流捕获 Redis 异常并按既定可用性决策 fail-open。

这保证了核心读取可用，但 Redis 故障期间登录/上传频控暂时减弱，因此不能把 fail-open 描述为“依赖健康”。

## 时间线

1. 验证 health=200、统计=200，并确认 Redis 已连接。
2. 停止固定隔离项目中的 Redis 服务。
3. 观察 health=503，记录依赖异常信号。
4. 执行新的认证请求，得到 200；查询统计，得到 200 和 MySQL 事实。
5. 启动同一 Redis 服务，以认证 `redis-cli ping` 与 HTTP 轮询确认恢复。
6. 再次确认 health=200、统计=200，清理演练数据和临时凭据。

## 根因

直接原因是 Redis 容器停止，应用 Redis health indicator 无法建立连接。统计和限流没有把 Redis 作为业务事实源：统计路径捕获缓存故障并查询 MySQL；限流路径采用 fail-open，所以应用不因缓存/频控依赖一起失效。

## 恢复与验证

恢复动作只启动原 Redis 容器，应用无需重启。恢复完成需要同时满足：Redis ping 成功、应用 health=200、统计=200、统计结果与 MySQL 一致、缓存 key 可重新生成且 TTL 有界。

演练结束后没有删除普通开发 Redis 或任何命名卷；`finguard-day6` 的临时数据和资源按固定边界清理。

## 预防

- 保留 Redis health 告警和 Prometheus 可见性，不能因业务降级成功而忽略依赖异常。
- MySQL 继续作为统计、幂等和权限事实源。
- 对 fail-open 期间的认证/上传量建立安全监控，超出预期时升级处理。
- 故障脚本固定 Compose project，禁止对普通栈执行卷删除。

## 证据

- [Day 6 故障演练总复盘](../review/week6-day6-fault-drills.md)
- [Redis 故障演练脚本](../../scripts/drills/invoke-redis-outage-drill.ps1)
- [Redis fail-open ADR](../adr/0004-redis-fail-open.md)
- [依赖故障响应 Runbook](../runbooks/dependency-incident-response.md)
