# Week 6 Day 5 性能测试与证据驱动优化设计

## 1. 业务目标

Day 5 用一次可重复、可解释的真实压力测试回答四个问题：

1. 在明确的硬件、数据规模和并发模型下，FinGuard 核心查询的吞吐量、错误率、P95 和 P99 是多少；
2. 压力上升时，瓶颈发生在请求端、应用、连接池还是 MySQL；
3. 哪一项最小改动能够由 JMeter、Prometheus 和 `EXPLAIN ANALYZE` 共同证明有效；
4. 优化后业务正确性、JWT/RBAC、Flyway、监控和部署能力是否保持不变。

本日不追求虚构的“高并发”数字，也不把单机结果描述为生产容量。

## 2. 当前事实基线

- Day 4 已完成不可变镜像发布和授权 Alibaba Cloud ECS 部署；持久主机为 Ubuntu 22.04、2 vCPU、约 7.2 GiB 内存，六服务继续保留。
- 当前应用镜像对应 Git 提交 `7646704`，Flyway 最新版本为 V10，完整回归历史基线为 378/378；这些数字进入 Day 5 后必须重新验证。
- 应用已经暴露 HTTP、JVM、HikariCP 和三类低基数业务指标，Prometheus/Grafana 可作为压测时的服务端证据。
- `audit_logs` 的筛选索引以 `action_code` 或 `initiated_by` 开头，但 `GET /api/audit-logs` 默认按 `created_at DESC, id DESC` 分页；是否需要增加默认排序索引必须由合成规模数据上的真实执行计划与压测结果决定。
- 当前工作站没有 JMeter 命令；Day 5 使用固定版本的 Apache JMeter 二进制，下载后校验官方 SHA-512，不把工具二进制提交到仓库。

## 3. 正式测试环境

采用两层环境：

1. **本地隔离 Compose**：只用于验证 JMX、数据脚本、JWT、断言、报告生成和清理，不产出正式性能结论。
2. **Day 4 保留的 ECS**：作为正式被测环境。应用、Prometheus 和 Grafana仍只绑定服务器回环地址；JMeter 从本地工作站经 SSH 隧道访问应用，不开放新的公网端口。

正式报告必须记录：测试日期、Git SHA、镜像 digest、操作系统、CPU、内存、JMeter/Java 版本、数据规模、线程数、升压时间、持续时间、网络路径和已知限制。

## 4. 被测接口与数据模型

### 4.1 主场景

主场景固定为：

```text
已在准备阶段取得 ADMIN JWT
  → GET /api/audit-logs?page=1&size=20
  → Spring Security 校验 JWT
  → AuditLogService 只读事务
  → MyBatis-Plus count + page query
  → MySQL 按 created_at DESC, id DESC 返回第一页
```

选择该场景的原因：

- 是真实 ADMIN 运维查询，不是健康检查或静态页面；
- 每次请求同时覆盖 JWT、Spring MVC、连接池、MyBatis 和 MySQL；
- 默认无筛选分页具有明确、可用 `EXPLAIN ANALYZE` 验证的排序访问路径；
- 只读请求可重复执行，不会因压测产生额外业务写入。

登录不放入循环：BCrypt 登录和每分钟五次登录限流属于安全语义，不应污染业务查询压测。

### 4.2 数据夹具

建立带唯一前缀的 Day 5 合成数据：

- 1 个专用 ADMIN 用户；
- 50,000 个终态 `import_jobs`；
- 50,000 条 `CSV_UPLOAD_ACCEPTED` 审计记录；
- 时间分布覆盖连续区间，审计记录均满足 V4/V10 的外键、唯一键和检查约束。

种子脚本必须幂等识别自己的前缀；清理顺序固定为审计记录 → 导入任务 → 专用用户/角色关系。不得删除 Day 4 已有业务数据或命名卷。

## 5. 负载模型

JMeter 只用 CLI 模式执行并生成 JTL 与 HTML Dashboard。JMX 通过 `-J` 参数接收 `baseUrl`、内存中的 JWT、线程数、升压秒数和持续秒数；仓库、JTL、报告和日志不得保存账号密码或 Token。

每轮包括：

| 阶段 | 并发线程 | 升压 | 持续 | 用途 |
|---|---:|---:|---:|---|
| 冒烟 | 1 | 1 秒 | 10 秒 | 验证 200、JSON 契约和报告链路 |
| 预热 | 5 | 15 秒 | 60 秒 | 稳定 JVM、连接池和缓存 |
| Level 1 | 10 | 30 秒 | 120 秒 | 低负载基线 |
| Level 2 | 25 | 30 秒 | 120 秒 | 中负载基线 |
| Level 3 | 50 | 60 秒 | 120 秒 | 2 vCPU 主机的受控高负载观察 |

每个线程在请求间加入 100～300 ms 随机思考时间。优化前后使用同一数据、同一 JMX、同一负载参数、同一 SSH 隧道和尽可能接近的运行时段。

## 6. 指标与证据

每个正式 Level 至少记录：

- JMeter：样本数、实际吞吐量、错误率、平均值、中位数、P90、P95、P99、最大值和 HTTP 错误分布；
- Prometheus：请求速率/延迟、JVM heap、GC、进程 CPU、Hikari active/idle/pending、容器重启和应用健康；
- MySQL：表行数、索引清单、`EXPLAIN ANALYZE`、是否 `filesort`、扫描行数与实际执行时间；
- 环境：客户端与服务端 CPU/内存、磁盘余量、网络路径、镜像 SHA/digest。

性能通过标准不是预设 TPS 数字，而是：零业务错误、无 OOM/容器重启、报告字段完整、优化前后条件一致，并能解释吞吐量和延迟变化的原因。

## 7. 安全停止门槛

出现任一情况立即停止当前压力级别，不继续升压：

- HTTP 错误率超过 1%；
- `/actuator/health` 非 `UP`；
- 应用或依赖容器重启/OOM；
- 服务端 CPU 连续 60 秒超过约 90%；
- Hikari pending 持续 30 秒大于 0；
- 根文件系统可用空间低于 15%；
- 延迟持续恶化且吞吐量不再增加。

停止后只收集有限日志和只读诊断；不进入 Day 6 的故障注入。

## 8. 优化决策门

优化前必须同时具备：

1. JMeter 显示主场景在较高负载下出现可重复的延迟或吞吐瓶颈；
2. Prometheus 没有证明瓶颈仅来自压测机或网络；
3. MySQL `EXPLAIN ANALYZE` 证明默认审计分页发生不必要的全表扫描/排序，且新增访问路径与真实查询一致。

若三项成立，最小候选优化是新增 Flyway V11，为默认审计分页增加：

```sql
INDEX idx_audit_logs_created_id (created_at DESC, id DESC)
```

不得修改已应用的 V10。先写失败的数据库集成测试，证明最新迁移版本和索引访问路径尚不存在；再添加 V11，更新所有最新版本断言并验证测试转绿。若证据不支持该索引，则不强行优化，改为在验收报告中记录“未发现可解释优化”，不得为了完成百分比而修改线程池、连接池或 MQ 并发。

## 9. 交付文件

```text
docs/design/week6-day5-performance-testing-design.md
docs/review/week6-day5-performance-acceptance.md
docs/superpowers/plans/2026-08-12-week6-day5-performance.md
performance/README.md
performance/jmeter/audit-log-query.jmx
performance/scripts/install-jmeter.ps1
performance/scripts/run-audit-log-test.ps1
performance/sql/seed-audit-log-performance-data.sql
performance/sql/explain-audit-log-query.sql
performance/sql/cleanup-audit-log-performance-data.sql
src/main/resources/db/migration/V11__add_audit_log_default_pagination_index.sql  # 仅在证据门通过后
src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java            # 仅在 V11 落地时
TASKS.md
README.md
.gitignore
```

JTL、HTML Dashboard、JMeter 解压目录、临时 JWT、SSH 信息和凭据均为本地验收产物，不提交 Git。

## 10. 范围边界

Day 5 不做：

- 安全扫描、攻击性测试、故障注入和三次故障演练；
- 公开服务端口、Nginx、TLS、CD、Kubernetes 或分布式压测；
- 修改业务状态、JWT/RBAC、审计语义、Redis 限流、RabbitMQ ACK/重试/DLQ；
- 为追求数字盲目调整 Tomcat、Hikari、JVM、RabbitMQ 消费并发或 MySQL 全局参数；
- 伪造用户量、生产流量、性能提升或容量承诺。

## 11. 完成标准

- JMeter 安装包来源和 SHA-512 校验通过；
- JMX、种子、执行、`EXPLAIN` 和清理脚本可以重复执行；
- 本地冒烟和 ECS 正式优化前测试完成；
- 优化决策由 JMeter、Prometheus 和 `EXPLAIN ANALYZE` 共同支持；
- 若落地 V11，严格完成 TDD 红/绿、空库 Flyway V1→V11、聚焦测试和完整回归；
- 同条件优化后复测完成，并报告真实绝对值和计算方式；
- 业务/JWT/RBAC/监控 smoke 通过；
- 合成数据、临时 Token、JTL/HTML、进程和隧道按边界清理，Day 4 持久数据与卷保留；
- `git diff --check`、敏感信息扫描和范围审计通过后提交。
