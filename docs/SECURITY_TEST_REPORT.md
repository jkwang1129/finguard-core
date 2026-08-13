# FinGuard Core 安全与故障验证摘要

## 1. 最终判断

Week 6 Day 6 覆盖认证、授权、输入/CSV、SQL/持久化、秘密、容器/部署、Actuator/Redis、RabbitMQ 八类边界。没有确认可复现的 Critical/High 应用漏洞；确认并修复 1 个 Low：开发 Compose 的 MySQL 端口从所有网卡收紧到 `127.0.0.1`。

这不是渗透测试或合规认证。ZAP 只做本机、被动、未认证 Baseline；ECS/公网没有执行主动扫描。

## 2. 已验证控制

- BCrypt + 两小时 HS256 JWT，密钥长度、issuer、iat/exp 校验；无 Session。
- 匿名 401、角色不足 403；ADMIN/REVIEWER 路由矩阵真实 HTTP 通过。
- CSV 5 MiB、10,000 行、严格 UTF-8/RFC 4180、字段与领域校验。
- MyBatis 使用参数绑定；未发现请求值进入 `${...}` 动态 SQL。
- 数据库唯一键、CHECK、外键、行锁、条件更新和事务保护幂等与并发。
- 秘密只由运行环境注入；仓库、镜像内容、文档和生成结果不得保存真实值。
- Actuator 只暴露 `health,prometheus`；Linux 依赖不发布端口。
- RabbitMQ 并发/prefetch 有界，两级有限重试和独立 DLQ；消息与指标不携带敏感文本。

真实负向 HTTP 覆盖匿名/非法/过期 Token、错误 JSON、REVIEWER 越权、未暴露 Actuator 等；31 个认证/RBAC/CSV 聚焦测试全绿。

## 3. 扫描结论

Trivy 仓库扫描为 0 漏洞、0 配置错误、0 秘密。Day 6 镜像 OS 扫描报告 11 High、133 Medium、155 Low 供应链条目；对 6 个唯一 High CVE 做了应用路径和厂商状态复核，没有形成当前 HTTP 服务可利用的 High，但它们仍要求随基础镜像、数据库和厂商修复持续复扫。

ZAP 根路径和 Swagger UI Baseline 均为 0 Fail。Swagger 警告主要是 CSP/跨源隔离等防御纵深建议与第三方前端依赖提示；公开文档没有用户可写规范入口。若未来公网提供 Swagger，必须重新设计 CSP、TLS 和认证后扫描。

## 4. 故障演练

| 演练 | 现象与恢复 | 关键结论 |
| --- | --- | --- |
| Redis 中断 | health 503；登录和统计仍 200；启动同容器后恢复 200 | 缓存回源、限流 fail-open，依赖异常仍可观测 |
| RabbitMQ 持续/瞬时失败 | 排除竞争消费者后 5s/30s 两级重试、耗尽 DLQ、任务终态符合契约 | 演练必须保证唯一受控消费者并在 finally 恢复 |
| 错误 MySQL 端口 | 一次性 app `exited|1`；原健康实例保持 200 | 配置在数据源/Flyway 启动期快速失败，不改保留环境 |

## 5. 已知限制与后续动作

- Redis fail-open 期间登录和上传频控暂时失效，但 RBAC、数据库幂等和审计仍生效。
- 无 Token 主动撤销；用户状态/角色变化后，已签发 Token 最长可能继续有效两小时。
- 未实现 TLS、WAF、集中秘密管理、SBOM 门禁、认证后 ZAP、SAST/DAST 全覆盖和告警通知。
- 漏洞数据会漂移；每次发布应重建镜像、更新扫描数据库、核对厂商 VEX/errata。

详细证据：[Day 6 安全报告](review/week6-day6-security-report.md)、[Day 6 故障演练](review/week6-day6-fault-drills.md)。
