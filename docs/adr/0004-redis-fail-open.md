# ADR-0004：Redis 缓存回源与限流 fail-open

日期：2026-08-13

## 状态

Accepted

## 背景

Redis 用于统计缓存和登录/上传固定窗口限流。若 Redis 被当成唯一事实源，缓存丢失会造成业务错误；若 Redis 故障时所有请求 fail-closed，缓存/频控依赖会阻断核心登录和读取。个人项目当前没有 Redis 高可用集群。

## 决策

统计结果以 MySQL 为事实源，Redis 只保存固定 key 和有限 TTL；缓存 miss 或异常时回源 MySQL，业务事务提交后使缓存失效。登录/上传限流在 Redis 异常时 fail-open，允许核心请求继续；Actuator health 和监控仍暴露 Redis 不健康。

## 理由

- 缓存不承担权威数据，丢失或不可用不会改变业务正确性。
- 在当前项目边界内，核心可用性优先于短时频控完整性。
- health 503 与业务 200 可以同时表达“服务降级但依赖异常”，便于告警和恢复。

## 后果

Redis 故障期间数据库读取压力增加，登录/上传频控暂时削弱，存在安全风险；维护者必须监控异常时长和请求量。应用不能因为业务降级成功就隐藏 Redis health 故障。

## 替代方案

- **fail-closed**：频控更严格，但 Redis 单点会阻断登录和上传，不符合当前可用性取舍。
- **Redis 作为统计真源**：缓存丢失会产生错误结果，拒绝采用。
- **本地内存备用限流**：多实例不一致且增加另一套状态语义，当前不引入。

## 证据

- [Redis 缓存与限流设计](../design/week5-day6-redis-cache-and-rate-limit-design.md)
- [Redis 中断故障演练](../incidents/redis-outage.md)
- [监控与排障](../MONITORING.md)
- [安全测试报告](../SECURITY_TEST_REPORT.md)
