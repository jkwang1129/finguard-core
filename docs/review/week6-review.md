# Week 6 Final Review

日期：2026-08-13（Asia/Shanghai）

## Scope and baseline

Week 6 的目标是把前五周业务代码变成可构建、可运行、可观测、可部署、可验证、可演示的交付物。Day 7 没有新增业务接口、表、迁移、依赖或服务，只增加隔离验收工具、确定性样例和最终文档。

进入 Day 7 时本地 `main` 位于 `dd146e4`，比 `origin/main` 超前 1 个 Day 6 提交；Flyway 最新为 V11，历史测试为 379/379。下文的 Day 7 数字均来自本日重新执行，历史部署/性能/安全事实则链接到对应源记录。

## Day 1–Day 7 delivery map

| Day | 交付 | 主要提交/证据 |
| --- | --- | --- |
| Day 1 | Springdoc OpenAPI、JWT Bearer Scheme、18 路径演示契约 | `6ca94b8` |
| Day 2 | 非 root Java 17 镜像、四/六服务 Compose、GitHub Actions | `97fd641`, `5f5062f` |
| Day 3 | Micrometer 三组业务指标、Prometheus、Grafana 9 面板 | `6f0c284`, [验收](week6-day3-acceptance.md) |
| Day 4 | 完整 SHA GHCR 发布、Linux 私有拓扑、保留卷升级/回滚 | `7646704`, [验收](week6-day4-linux-deployment-acceptance.md) |
| Day 5 | 认证 JMeter/Prometheus/EXPLAIN 证据，V11 审计分页索引 | `577ef62`, `5f424e1`, [验收](week6-day5-performance-acceptance.md) |
| Day 6 | 安全验证、1 个 Low 修复、Redis/RabbitMQ/MySQL 故障演练 | `dd146e4`, [安全](week6-day6-security-report.md), [故障](week6-day6-fault-drills.md) |
| Day 7 | 全量复验、最终文档、演示/面试/简历和 Git/CI 收口 | `43da42e` |

## Day 7 fresh baseline

| Check | Command/evidence | Observed result | Status |
| --- | --- | --- | --- |
| Maven JVM | `mvn -version` | Maven 3.9.16，Microsoft Java 17.0.10 | PASS |
| Docker | `docker version` | client/server 29.6.1 | PASS |
| Compose | `docker compose version` | v5.3.0 | PASS |
| Git | `git status --short --branch` | 开始时 `main...origin/main [ahead 1]` | INFO |
| Compose config | Day 7 preflight | 开发与 Linux 配置均可解析 | PASS |
| 应用镜像 | inspect + 容器内命令 | 168,830,390 bytes；Temurin 17.0.19；UID/GID 10001 | PASS |
| 镜像内容 | 容器内检查 | 有 `/opt/finguard/app.jar`；无 Maven、源码、`.env`、工作区 | PASS |

宿主机 PATH 中的独立 `java` 是 21.0.11，但 Maven 实际使用 Java 17.0.10；测试和项目验收以 Maven JVM 为准。镜像运行时为 Java 17。

## Automated regression

2026-08-13 执行：

```text
mvn -B -ntp clean test
Tests run: 379, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 03:38 min
```

测试连接真实本机 MySQL 8.4、RabbitMQ 和 Redis；Flyway 校验 11 个迁移，schema 当前版本为 11。日志中的 Redis offline 堆栈来自明确验证 fail-open/reset 降级的测试，不是测试失败。

主交付提交 `43da42e` 推送后，GitHub Actions [run 31678759392](https://github.com/jkwang1129/finguard-core/actions/runs/31678759392) 完成且结论为 `success`：`Test, package, and build image` 与 `Publish immutable application image` 两个 job 均通过，后者实际完成完整 SHA 镜像构建/检查/推送、Linux 部署生命周期和验收资源删除。

## Six-service runtime acceptance

固定隔离项目 `finguard-day7` 使用独立端口和 5 个独立命名卷启动 app、MySQL、RabbitMQ、Redis、Prometheus、Grafana。实测结果对象：

| Gate | Result |
| --- | --- |
| `ServicesHealthy` | `true` |
| `FlywayVersion` | `11` |
| `OpenApiPathCount` | `18` |
| `PrometheusTargetUp` | `true` |
| `BusinessFlowPassed` | `true` |
| `RestartPassed` | `true` |
| `CleanupPassed` | `true` |

Grafana `/api/health` 成功；应用镜像由当前工作树构建，六服务均经过健康轮询，而不是仅依赖容器进程存在。

## JWT/RBAC and business flow

验收在临时环境中随机生成 JWT/服务密码和 ADMIN/REVIEWER 凭据。匿名账户请求返回 401；REVIEWER 读取为 200、写入为 403；ADMIN 不能执行 REVIEWER 决策，REVIEWER 可完成带 version 的决策。

API 创建 `DAY7-DEMO` 账户和人工匹配交易，上传确定性 CSV 后导入到终态，重复文件返回原任务；创建对账任务并轮询到终态，重复请求不创建第二任务。大额行产生 `LARGE_AMOUNT` 风险与审核任务，REVIEWER 决策后审计和统计一致。验收 ID 均来自独立空库，只用于证明本次链路，不作为长期业务数据。

## MySQL/RabbitMQ/Redis/monitoring evidence

- MySQL：Flyway V11，账户、交易、导入、对账、风险、审核、审计事实与 API 响应一致。
- RabbitMQ：导入/对账主队列最终无 ready/unacked；异步任务由真实消费者完成。
- Redis：统计固定 key 存在且可解析，TTL 合理；清理时隔离卷整体删除。
- Prometheus：应用 target 为 `UP`，三组业务指标可查询；应用重启后 target 恢复。
- Grafana：健康通过，provisioning 无需人工创建数据源和面板。

## Deployment/performance/security/fault evidence

- Day 4 已在授权 ECS 上验证完整 SHA 镜像、私有依赖端口、回环监控、Bootstrap 关闭和保留卷跨 SHA 回滚；这属于[历史真实部署证据](week6-day4-linux-deployment-acceptance.md)，Day 7 不把本地配置存在冒充当前在线状态。
- Day 5 以 50,002 条审计数据证明 V11 把默认分页变为取 20 行的索引扫描；优化后外部 JMeter 因 Token 损坏全为 401，不进入性能提升结论，详见[性能验收](week6-day5-performance-acceptance.md)。
- Day 6 未确认可复现 Critical/High 应用漏洞，修复 1 个开发端口 Low；镜像供应链告警保留持续复扫义务，详见[安全报告](week6-day6-security-report.md)。
- Redis 降级、RabbitMQ 两级重试/DLQ 和错误 MySQL 配置均完成现象、根因、恢复、预防闭环，详见[故障复盘](week6-day6-fault-drills.md)。

## Cleanup and resource isolation

验收脚本在 `finally` 中删除固定 `finguard-day7` 的 6 个容器、网络、5 个命名卷和临时环境目录。退出后再次按 Compose label 查询，Day 7 容器、卷和网络均为 0；临时 JWT/密码未写入仓库。普通开发 MySQL/RabbitMQ/Redis 容器在验收前后保持存在，未执行普通栈 `down -v`。

Day 7 使用隔离空卷，因此业务夹具随验收卷删除，不需要对普通开发数据库执行危险的级联删除。最终审计再次确认 Day 7 镜像和监听端口为 0、无生成结果或秘密进入提交；主提交推送后本地 `main` 与 `origin/main` 同步。

## Documentation and resume claim audit

最终入口包括 README、架构、数据库/ER、API、演示、部署、监控、性能、安全、面试、简历和本复盘。Markdown 本地链接已执行仓库扫描。简历只使用可追溯表述：379/379、V11、18 路径、9 面板、索引访问路径、安全 Low 修复和三次故障演练；明确禁止生产 SLA、并发容量、Exactly Once、零漏洞和高可用等无证据声明。

## Key decisions and trade-offs

1. 单体保持清晰事务边界，消息与缓存只承担必要职责，不为“微服务”标签提前拆分。
2. MySQL 是任务/幂等/审计真源，RabbitMQ 是至少一次通知，Redis 是可降级加速与频控。
3. Outbox 解决数据库与消息双写窗口，消费者仍用行锁、终态和唯一键抵抗重投。
4. REVIEWER 独占决策、ADMIN 独占业务写/审计查询，形成职责分离。
5. 指标标签低基数；审计摘要和错误响应不泄露原始敏感内容。
6. 只在 JMeter、Prometheus、EXPLAIN 对齐后添加一个索引，不做猜测型优化。
7. 部署按不可变 SHA，回滚保留卷但不伪装为数据库回滚或灾备。

## Problems encountered and root causes

- 首次 Day 7 预检读取 PATH `java -version`，得到 Java 21 且 stderr 影响脚本。根因是 PATH Java 与 Maven JVM 不同；改为解析 `mvn -version`，锁定真正执行测试的 Java 17。
- PowerShell 5.1 的 `Invoke-WebRequest.Content` 在当前环境返回字节数组，直接 `ConvertFrom-Json` 失败。新增 UTF-8 JSON 解码助手并先写失败测试。
- Compose build 输出进入 PowerShell 成功管道，污染验收结果对象。Compose 调用改为显式输出到主机，结果对象保持机器可读。
- 首次最终清理已经删除镜像后，`docker image inspect` 用“镜像不存在”的 stderr 作为存在性判断；PowerShell 5.1 在 Stop 模式把预期 stderr 提升为终止异常。回归测试先复现，再改用无错误输出的 `docker image ls --filter` 判断并完整重跑通过。
- Day 5 外部复测 JWT 在传递中被截断/换行；单请求未先证明 200。结论是 401 数据作废，后续负载入口必须先执行真实请求 200 门禁。
- Day 6 消息演练存在竞争消费者、首次全回归存在隔离库污染；通过唯一消费者边界和重建仅验收卷定位为环境问题，没有把它误修成业务代码。

## Known limitations

- 单机 Compose，无 TLS、负载均衡、多可用区、集中秘密管理和自动备份恢复。
- JWT 无刷新与主动撤销；旧 Token 最长两小时有效。
- Redis 限流 fail-open 时频控暂时失效。
- Outbox、审计和业务表没有长期归档/分区策略。
- ZAP 为未认证被动基线，安全验证不等于完整渗透或合规认证。
- 有效性能证据只覆盖审计默认分页；没有生产并发/SLA 结论。
- 应用镜像回滚不回滚 Flyway 或业务数据。

## Interview questions

1. Outbox 解决了什么双写窗口，为什么仍需要消费者幂等？
2. 手动 ACK、任务行锁、唯一约束分别保护哪一段故障？
3. 401 与 403 如何在真实过滤链中区分？
4. 为什么 REVIEWER 能决策但 ADMIN 不能？
5. Redis fail-open 的收益、风险和监控信号是什么？
6. 为什么指标不能带任务 ID、用户名和异常文本？
7. 如何证明一个索引是必要且有效的？
8. 为什么优化后全 401 的 JMeter 数据必须作废？
9. 应用镜像回滚与数据库回滚有什么不同？
10. 如何区分业务缺陷、竞争消费者和验收数据污染？

完整参考答案见[面试笔记](../INTERVIEW_NOTES.md)。

## Six-week learning outcome and next steps

六周从账户/交易 CRUD 开始，逐步补齐认证授权、CSV 与对账、异步可靠性、风险审核/审计/Redis，再进入容器、CI、观测、Linux、性能、安全与故障恢复。最终产出不只是“写过 Spring Boot”，而是一条能从测试、HTTP、数据库、中间件、指标、部署、清理和 Git/CI 相互证明的工程证据链。

下一步优先级：TLS/反向代理与集中秘密管理 → 数据库备份/恢复演练 → SBOM/依赖扫描门禁与告警通知 → 修复后认证 JMeter 对比 → 根据真实规模评估归档、分区、消费扩容。微服务或 Kubernetes 必须由容量、团队和边界证据驱动，不作为默认升级。
