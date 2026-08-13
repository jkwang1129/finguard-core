# FinGuard Core 简历表述

## 项目简介

FinGuard Core｜Java / Spring Boot 交易导入、对账与风控审核平台（个人项目）

## 可直接使用的项目要点

- 基于 Java 17、Spring Boot、MyBatis-Plus 与 MySQL 设计账户/交易、CSV 异步导入、自动对账、风险命中、人工审核和审计统计闭环；通过唯一约束、行锁、条件更新和事务保证重复请求、并发审核与消息重投下的数据一致性。
- 采用 Transactional Outbox + RabbitMQ 手动 ACK 构建异步导入/对账链路，支持 5 秒/30 秒有限重试和独立 DLQ；以数据库任务终态为真源，实现重复消息和 ACK 丢失下的幂等处理。
- 实现 BCrypt + HS256 JWT 无状态认证、ADMIN/REVIEWER RBAC、Redis 统计缓存与固定窗口限流，并用 Micrometer、Prometheus、Grafana 9 面板观测 HTTP/JVM/HikariCP 和低基数业务指标。
- 构建非 root Java 17 Docker 镜像、六服务 Compose、GitHub Actions 与完整 SHA 的 Linux 发布/保留卷回滚流程；Java 17 全量回归 379/379，独立验收验证 Flyway V11、OpenAPI 18 路径、六服务健康和真实业务闭环。

## 性能/安全补充表述（按版面选择）

- 基于 50,002 条审计数据的 `EXPLAIN ANALYZE` 定位默认分页全表扫描与排序，新增 `(created_at DESC,id DESC)` 复合索引后变为取 20 行的索引扫描；保留 JMeter/Prometheus/SQL 三类证据边界。
- 完成源码、依赖、镜像、秘密、被动 Web 基线与 JWT/RBAC 负向验证，修复开发 MySQL 端口外部暴露 Low 问题；在隔离环境完成 Redis 降级、RabbitMQ 重试/DLQ 和错误 MySQL 配置恢复演练。

## 面试证据映射

| 表述 | 可展示证据 |
| --- | --- |
| 业务与一致性闭环 | [架构说明](ARCHITECTURE.md)、[数据库](DATABASE.md)、V1–V10 迁移与集成测试 |
| Outbox/重试/DLQ | Week 4 设计、[故障演练](review/week6-day6-fault-drills.md) |
| JWT/RBAC/Redis/监控 | [API](API.md)、[监控](MONITORING.md)、[Day 3 验收](review/week6-day3-acceptance.md) |
| 容器/CI/Linux | [部署](DEPLOYMENT.md)、[Day 4 验收](review/week6-day4-linux-deployment-acceptance.md) |
| 379/379 与六服务闭环 | [Week 6 最终复盘](review/week6-review.md) |
| 性能索引 | [性能报告](PERFORMANCE_REPORT.md) |
| 安全与恢复 | [安全摘要](SECURITY_TEST_REPORT.md) |

## 禁止夸大的内容

- 不写“生产系统”“银行核心”“支持真实资金交易”或任何真实用户/资金规模。
- 不写“支持 N 并发”“性能提升 N%”或 SLA；优化后 ECS JMeter 复测为 401，只有 SQL 访问路径得到有效证明。
- 不写“零漏洞”或“通过渗透测试”；实际是未确认可复现 Critical/High 应用漏洞，且镜像供应链告警需持续复核。
- 不写“Exactly Once”；实际是至少一次消息传递 + 业务幂等。
- 不写“高可用/灾备完成”；当前是单机 Compose 和保留卷应用回滚，没有跨机备份恢复。
