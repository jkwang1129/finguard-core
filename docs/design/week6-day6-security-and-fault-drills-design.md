# Week 6 Day 6：安全验证与故障演练设计

日期：2026-08-12

## 1. 业务目标

Day 6 不新增业务能力，而是回答两个生产化问题：当前 FinGuard Core 的安全控制是否能被证据验证，以及 Redis、RabbitMQ 或运行配置发生故障时，系统是否能按既有设计降级、隔离并恢复。

最终交付一份安全测试报告和三次可复现故障复盘。所有结论必须来自当前源码、自动化测试、扫描结果或真实运行证据，不能把工具告警直接写成漏洞。

## 2. 当前事实基线

- Week 6 Day 5 已在 `5f424e1` 收口，历史回归为 379/379；Day 6 必须重新实跑。
- Flyway 最新版本为 V11，本日默认不新增迁移，也不修改 V1～V11。
- Linux 部署使用完整 Git SHA 镜像、回环端口、SSH 隧道和权限为 600 的私有环境文件。
- Actuator 只暴露 `health` 与 `prometheus`。
- Redis 统计缓存失败时回退 MySQL；登录与上传限流在 Redis 不可用时 fail-open。
- RabbitMQ 消费失败使用两级延迟重试，耗尽后进入 DLQ，并记录低基数失败指标。

## 3. 环境与风险边界

采用“隔离环境演练、保留环境只读复核”：

- 扰动操作只针对独立 Compose project `finguard-day6` 及其独立容器、网络和命名卷。
- 不执行 `docker compose down -v` 操作普通开发栈或 Linux 保留栈。
- 不对公网目标执行主动 DAST；ZAP 只使用 Baseline 被动扫描。
- 不在报告、日志、JTL、命令输出或 Git 中保存 JWT、密码、私钥、主机地址和 Registry Token。
- ECS 如可访问，只做 SSH 隧道下的只读端口、健康、权限和 Prometheus 复核；没有新授权时不停止其服务。
- 出现非目标数据变化、保留资源受影响、恢复失败、真实秘密暴露或无法解释的高危问题时立即停止。

## 4. 安全验证模型

检查面按 OWASP ASVS 的项目适用子集组织：

1. 认证：登录失败一致性、BCrypt、JWT 签名/issuer/过期、无敏感 Claims。
2. 授权：匿名 401、角色不足 403、ADMIN/REVIEWER 路由矩阵。
3. 输入与文件：Bean Validation、分页上限、JSON 错误、CSV 大小/编码/表头/行数。
4. 数据访问：MyBatis 参数绑定、唯一约束、事务、乐观锁和错误响应脱敏。
5. 配置与秘密：环境注入、Git/镜像秘密扫描、非 root、最小端口和最小 Actuator 暴露。
6. 组件风险：Maven 依赖与最终容器镜像中的已知漏洞。
7. Web 基线：对匿名文档与未认证业务面执行短时 ZAP Baseline 被动扫描。

工具结果按 `confirmed`、`false-positive`、`mitigated`、`accepted` 分类。可复现的 Critical/High 必须修复或阻塞 Day 6；Medium/Low 必须记录影响、前提和现有补偿控制。

## 5. 三次故障演练

### 5.1 Redis 中断与恢复

停止隔离 Redis，验证健康状态变化、统计查询回退 MySQL、登录/上传限流 fail-open、日志脱敏，再恢复 Redis 并验证缓存和限流重新生效。演练不把 fail-open 描述为“无风险”，而是明确可用性优先的安全取舍。

### 5.2 RabbitMQ 重试与 DLQ

复用真实 RabbitMQ/MySQL 集成测试中的可控处理器故障，验证约 5 秒和 30 秒两级延迟、`x-finguard-retry-attempt=2`、`RETRY_EXHAUSTED`、任务失败、DLQ 隔离以及没有重复业务写入。运行后清空测试专用消息和数据。

### 5.3 错误依赖配置与恢复

使用一次性应用容器和错误 MySQL 端口触发启动失败，按 `compose ps → 有限 logs → inspect → 端口/依赖检查 → 根因 → 修复` 排查。随后使用正确配置恢复并验证健康、Flyway V11、OpenAPI、JWT/RBAC 与业务数据。

## 6. 可重复工具

```text
security/
  README.md
  zap-baseline.conf
  scripts/run-static-security-checks.ps1
  scripts/run-zap-baseline.ps1
  tests/test-security-tools.ps1
scripts/drills/
  invoke-redis-outage-drill.ps1
  invoke-messaging-retry-drill.ps1
  invoke-misconfiguration-drill.ps1
docs/review/
  week6-day6-security-report.md
  week6-day6-fault-drills.md
```

扫描器缓存、原始 JSON/SARIF/HTML、临时环境文件和 Token 只放在被忽略的 `security/results/`，Git 只保存可复现脚本、规则、摘要和脱敏证据。

## 7. 测试与验收

- PowerShell 工具测试先失败后实现，覆盖参数拒绝、结果目录、秘密输出保护和 dry-run 命令边界。
- 安全聚焦测试覆盖认证、RBAC、上传/输入和端点暴露。
- RabbitMQ 故障聚焦测试验证一次恢复和重试耗尽两条路径。
- 完整执行 Java 17 `mvn -B -ntp clean test`，不得跳过测试。
- 独立 Compose 环境完成健康、OpenAPI、JWT/RBAC、Redis 降级/恢复和错误配置恢复。
- 结束时临时数据、队列、结果、环境文件、进程、容器、网络和 Day 6 卷均清理；普通开发容器与数据不变。
- `git diff --check`、秘密扫描和范围审计通过后才能提交。

## 8. 范围边界

Day 6 不实现 TLS/Nginx/WAF、OAuth/MFA、Vault、Kubernetes、SIEM、主动渗透、数据库备份系统、管理端 DLQ 重放或新的业务接口。若扫描发现真实问题，只做能够由测试证明且不扩张架构的最小修复；Day 7 的最终 README、架构图、演示材料、面试笔记和简历描述不提前完成。
