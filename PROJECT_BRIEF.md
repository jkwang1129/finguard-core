# FinGuard Core 新项目交接与学习执行说明

> 文档版本：2026-07-17  
> 用途：交给新建的 FinGuard Core Codex 项目阅读，作为当前项目范围、学习方式和执行计划的唯一事实来源。  
> 重要：本文记录的是多轮讨论后收敛出的最终方案。早期“大而全”的企业级方案仅作为长期方向，不能重新加入当前 6 周主线。

---

## 0. 给接手本项目的 Codex

请先完整阅读本文，再执行任何代码修改。

必须遵守以下原则：

1. 当前目标是 6 周达到第一段 Java 后端实习的项目标准，不是培养高级程序员。
2. 不得一次性生成完整项目，不得为了简历堆砌技术栈。
3. 每次只推进一个可运行、可测试、可提交的小里程碑。
4. 修改代码前，先说明业务目标、相关知识、设计取舍、拟修改文件和验收标准。
5. 关键业务代码优先让用户编写；Codex 提供骨架、TODO、提示和 Code Review。
6. 样板配置、重复 DTO、基础测试数据等可以由 Codex 完成，但需要解释用途。
7. 每个里程碑都必须实际编译、运行或测试，不能只给代码不验证。
8. 每个里程碑结束后给出 Git 提交建议、知识总结、常见错误和面试问题。
9. 如果进度落后，优先删除 P2 内容，不能牺牲 P0 业务闭环、测试和面试基础学习。
10. 禁止虚构用户数、并发量、吞吐量、性能提升和生产经验。

---

## 1. 用户目标与约束

### 1.1 当前目标

用户希望通过项目式学习，在 6 周内完成一个能够：

- 写入第一段 Java 后端实习简历；
- 独立运行和演示；
- 清晰解释业务、架构和技术取舍；
- 应对项目深挖、MySQL、Redis、RabbitMQ、Spring 等常见面试问题；
- 形成从需求分析到测试、部署的完整开发经历。

### 1.2 时间条件

- 总周期：6 周；
- 每天可学习：8 小时以上；
- 第 4 周末项目主流程可运行后开始投递；
- 项目、算法和计算机基础必须并行推进。

### 1.3 当前能力与长期方向

- 当前阶段以国内 Java 后端第一段实习为第一优先级；
- Spring Boot 仍处在项目实践积累阶段；
- 前端不是学习重点；
- 长期希望具备进入互联网公司做 Java 后端或 AI 应用/Agent 开发的能力；
- AI Agent 不进入当前 6 周 Core 项目，未来单独创建 FinGuard Copilot。

---

## 2. 前期项目比较与最终选择

前期比较过四类项目：苍穹外卖、黑马点评、智能记账系统和 FinGuard。

| 项目 | 主要价值 | 局限 | 当前用途 |
|---|---|---|---|
| 苍穹外卖 | 业务闭环、标准 Spring Boot 项目结构、常见企业接口 | 培训项目同质化严重 | 学习业务闭环和工程规范，不作为最终主项目 |
| 黑马点评 | Redis、缓存、分布式锁、秒杀、异步处理 | 简历重复度高，容易被认为照搬课程 | 学习 Redis 场景和高并发思想 |
| 智能记账系统 | 容易上手，已有用户、账户、交易、预算、统计设计 | 如果只做 CRUD，简历辨识度不足 | 作为早期需求和数据模型参考 |
| FinGuard Core | 文件导入、自动对账、幂等、异常审核、审计，业务辨识度高 | 如果做成完整企业平台，6 周无法完成 | 当前唯一主项目，写入实习简历 |

最终决定：

- 苍穹外卖和黑马点评只作为学习资料；
- 智能记账系统作为早期设计参考；
- 新建独立仓库，从零开发 FinGuard Core；
- 简历中不同时堆放四个高度重叠的项目。

---

## 3. 项目最终定位

### 3.1 项目名称

**FinGuard Core——交易导入、自动对账与异常审核平台**

简历中标注为个人项目，不宣传为真实生产系统。

### 3.2 核心业务闭环

```text
用户登录
  → 上传 CSV 交易流水
  → 文件哈希去重
  → RabbitMQ 异步解析
  → 数据校验与交易入库
  → 自动对账
  → 识别未匹配、重复或可疑交易
  → 生成异常审核任务
  → REVIEWER 确认或忽略
  → 记录审计日志和统计结果
```

项目的辨识度来自这条完整业务链，而不是使用了多少中间件。

### 3.3 架构原则

- 架构形态：模块化单体；
- 部署形态：一个 Spring Boot 应用；
- 数据真源：MySQL；
- Redis 只作为缓存、限流和短期幂等辅助；
- RabbitMQ 用于异步导入和对账；
- 不做微服务拆分；
- 不做完整 DDD，只做轻量领域建模和清晰模块边界。

建议模块：

```text
auth
account
transaction
importjob
reconciliation
risk
review
audit
common
```

---

## 4. 最终技术栈

### 4.1 主线技术栈

```text
Java 17
Maven
Spring Boot 3
Spring MVC
Spring Security
JWT
Jakarta Validation
MyBatis-Plus
MySQL
Flyway
Redis
RabbitMQ
JUnit 5
Mockito
Spring Boot Test
OpenAPI / Swagger
Docker
Docker Compose
Git / GitHub
SLF4J + Logback
Spring Boot Actuator
Micrometer
Prometheus
Grafana
GitHub Actions
Linux
JMeter（压力测试优先）
```

### 4.2 可选技术

只有 P0、P1 已稳定完成时才考虑：

- 极简管理页面：最多 1～2 天；
- MinIO 单机对象存储：最多 1 天；
- Testcontainers：优先只覆盖 MySQL 或 RabbitMQ 核心集成测试；
- Elasticsearch：仅在目标岗位明确要求且核心项目提前完成时考虑。

### 4.3 当前阶段明确不实现

- 完整多租户 SaaS；
- OAuth 2.0、OIDC；
- JPA/Hibernate；
- PostgreSQL；
- Kafka、RocketMQ；
- Elasticsearch 默认实现；
- 微服务和 Spring Cloud；
- Nacos、Gateway、OpenFeign；
- Sentinel、Seata；
- Kubernetes；
- OpenTelemetry；
- 数据库复制和分库分表；
- Drools 等复杂规则引擎；
- AI Agent；
- 复杂前端。

这些技术部分需要知道基本概念，但不能加入当前 6 周主线，也不能在未实现时写进简历。

---

## 5. 功能优先级

## 5.1 P0：必须 100% 完成的业务闭环

- 用户登录；
- Spring Security + JWT；
- ADMIN、REVIEWER 权限；
- 账户管理；
- 交易记录管理；
- 分页、条件查询；
- 参数校验；
- CSV 上传；
- 文件格式和逐行数据校验；
- SHA-256 防止重复导入；
- 自动对账；
- 匹配、未匹配、重复、可疑结果；
- 异常审核；
- 简单状态流转；
- 关键操作审计日志；
- Swagger 接口文档；
- Docker Compose 一键启动依赖；
- 核心单元测试和集成测试。

## 5.2 P1：计划在 6 周内完成的简历亮点

- Redis 缓存统计结果；
- Redis 限制登录和上传频率；
- RabbitMQ 异步导入和对账；
- RabbitMQ Publisher Confirm 和消费者手动 ACK；
- MQ 消费者幂等；
- 消息重试；
- 死信队列；
- 数据库唯一索引兜底；
- 乐观锁避免重复审核；
- 2～3 条简化风险规则；
- Actuator 健康检查；
- Micrometer 业务指标；
- Prometheus 指标采集和一个 Grafana 仪表盘；
- GitHub Actions 自动编译、测试、打包和构建 Docker 镜像；
- 一次真实 Linux 部署及部署文档；
- 一次真实压力测试；
- 根据测试结果完成一次可解释的性能优化；
- 至少三次可复现的故障演练和复盘；
- 一份安全测试报告。

## 5.3 P2：可删除内容

按优先顺序考虑：

1. 极简管理页面；
2. MinIO；
3. Testcontainers；
4. Elasticsearch，仅在目标岗位明确要求且核心项目提前完成时考虑。

任何 P2 功能超出时间盒，立即回退，不拖累主线。

---

## 6. 核心业务设计约束

### 6.1 对账规则

当前版本不做复杂机器学习或模糊算法，只实现可解释规则：

- 外部流水号是否一致；
- 金额是否一致；
- 日期是否在允许范围内；
- 是否为重复交易；
- 输出 MATCHED、UNMATCHED、DUPLICATE、SUSPICIOUS 等结果。

### 6.2 简化风险规则

不使用 Drools，使用策略模式或责任链实现最多三条规则：

```text
LargeAmountRule
DuplicateTransactionRule
FrequentTransactionRule
```

规则系统只需支持：

- 独立规则类；
- Spring 注入规则实现；
- 规则启用状态或简单阈值；
- 命中原因；
- 单元测试。

不开发规则 DSL、可视化配置平台、规则版本平台或复杂冲突解决机制。

### 6.3 状态流转

导入任务：

```text
PENDING → PROCESSING → SUCCESS / PARTIAL_SUCCESS / FAILED
```

审核任务：

```text
PENDING → CONFIRMED / IGNORED
```

使用枚举、Service 层状态校验和乐观锁，不引入专门状态机框架。

### 6.4 多层幂等

这一部分不能删除：

1. 文件 SHA-256：防止同一文件重复上传；
2. 数据库唯一索引：作为最终一致性兜底；
3. MQ 消费记录或业务唯一键：防止消息重复消费；
4. 乐观锁：防止审核任务重复提交；
5. 必要时使用 Redis 短期幂等键，但不能只依赖 Redis 保证最终正确性。

### 6.5 审计范围

只记录关键业务操作：

- CSV 上传；
- 导入失败；
- 对账完成；
- 审核确认；
- 审核忽略。

不记录每一次普通查询，不构建复杂合规审计平台。

### 6.6 金额和时间

- 金额统一使用 `BigDecimal`；
- 明确数据库精度和舍入规则；
- 当前版本默认人民币；
- 当前版本固定业务时区，不做多币种和全球时区平台；
- 不使用 `double` 保存金额。

---

## 7. 建议数据表

以下仅作为第一版设计输入，正式建表前仍需完成需求分析和 ER 图：

```text
users
roles
user_roles
accounts
transactions
import_jobs
import_row_errors
reconciliation_records
review_tasks
audit_logs
mq_consume_records（如采用消费记录表实现幂等）
```

关键约束至少考虑：

- 用户名或邮箱唯一；
- 文件哈希唯一；
- 外部流水号与账户的组合唯一性；
- 审核任务版本号；
- 导入任务和交易之间的关联；
- 对账结果与审核任务之间的关联；
- 必要的创建时间、更新时间和软删除策略。

不要在没有明确业务意义时增加大量字段和表。

---

## 8. 六周执行计划

| 周次 | 主要交付 | 核心知识 |
|---|---|---|
| 第 1 周 | 环境、Git、Spring Boot 骨架、数据库、账户和交易 CRUD | Java、Maven、MVC、分层、MySQL、REST |
| 第 2 周 | Security、JWT、RBAC、分页、事务、索引、Flyway | Spring Security、AOP、事务、索引、EXPLAIN |
| 第 3 周 | CSV、数据校验、SHA-256 去重、同步版自动对账 | Java I/O、异常处理、批处理、幂等、状态流转 |
| 第 4 周 | RabbitMQ 异步导入、生产者确认、手动 ACK、消费幂等、重试和死信队列 | 消息可靠性、重复消费、最终一致性 |
| 第 5 周 | Redis、风险规则、异常审核、乐观锁、审计、测试 | 缓存、限流、设计模式、并发控制、JUnit |
| 第 6 周 | Docker、真实 Linux 部署、GitHub Actions、Prometheus/Grafana、压力与安全测试、故障演练、文档和简历 | CI/CD、监控、部署、性能分析、故障排查、项目表达 |

投递节点：第 4 周末，如果 P0 和 RabbitMQ 主流程已经稳定运行，开始投递实习；后续边投递边完善。

---

## 9. 每天学习时间的弹性分配

每天可投入 8 小时以上是时间条件，不是固定配额。根据当天里程碑的任务量、难度、阻塞情况和精力动态调整，不要求项目、算法、基础知识和复盘每天都按相同比例进行。

分配原则：

- 编码、联调或排错任务较重时，以 FinGuard 开发、测试和调试为主；
- 需求设计、知识准备或项目任务较轻时，增加算法和 Java / JVM / MySQL / Redis / 网络 / 操作系统学习；
- Git、文档、复盘、简历和投递按里程碑集中完成，不固定每天预留 0.5 小时；
- 某条学习线可以在任务繁重的当天压缩或暂停，但应在本周后续时间补回；
- 每周验收时检查项目、算法和基础知识是否都有实质进展，并根据短板调整下一周安排。

项目负责简历筛选和项目深挖；算法与基础知识负责笔试与基础面试。三条线不能互相替代，但无需每天平均分配。

---

## 10. 项目对 Java 后端技能地图的覆盖

### 10.1 粗略覆盖结论

- 项目本身直接实践约 50% 的完整 Java 后端技能地图；
- 配合专项微实验，可以接触约 60%～65%；
- 真正达到“能使用、能解释、能排错”的内容主要是 Spring Boot、MySQL、Redis、RabbitMQ、测试和 Docker；
- 不能把“项目中用过一次”等同于“掌握”。

| 能力领域 | 项目覆盖程度 | 结论 |
|---|---:|---|
| 计算机基础 | 低，约 20%～30% | 必须单独学习 |
| Java 语言与运行时 | 中高，约 65% | JVM 和 JUC 需要实验补充 |
| Web 与 Spring | 高，约 80% | 项目主战场 |
| 数据与中间件 | 中高，约 70% | 深入 MySQL、Redis 和一种 MQ |
| 软件工程 | 高，约 75% | 无法替代真实团队协作 |
| 生产与运维 | 中低，约 40%～50% | 必须真正部署和排错 |
| 架构、安全与性能 | 中，约 50% | 只做实习级实践 |
| 职业能力 | 中高 | 项目表达能训练，真实团队经验不能模拟 |

### 10.2 项目不能替代的高优先级内容

即使不在项目里，也会直接影响笔试和面试：

- 数组、哈希、链表、栈队列、树、堆、图、二分、动态规划基础；
- Java 集合、泛型、异常、I/O、反射；
- JUC、线程池、锁、`volatile`、CAS、ThreadLocal；
- JVM 内存、类加载、GC、OOM 和死锁排查；
- MySQL 索引、事务、隔离级别、锁、MVCC、`EXPLAIN`；
- Redis 缓存穿透、击穿、雪崩、持久化和分布式锁原理；
- TCP、DNS、HTTP、HTTPS、TLS；
- 进程、线程、虚拟内存和 I/O；
- Linux、Git、日志和端口排查。

### 10.3 第一段实习可以延后的内容

- 同时掌握 MySQL 和 PostgreSQL；
- 同时掌握 RabbitMQ、Kafka 和 RocketMQ；
- 完整 OAuth 2.0/OIDC；
- 完整微服务和服务治理；
- Elasticsearch 集群；
- Kubernetes；
- OpenTelemetry；
- 生产级分库分表；
- 完整 DDD；
- 深入 CPU 微架构；
- AI Agent。

---

## 11. 项目额外训练的业务工程能力

这些内容不是“多几种技术”，而是让项目不退化成普通 CRUD 的核心深度。

### 11.1 金融数据正确性

- `BigDecimal`；
- 金额精度和舍入；
- 日期和交易时间；
- 重复交易识别；
- 数据不能被随意覆盖。

### 11.2 数据导入流水线

```text
文件上传
→ 文件校验
→ 哈希去重
→ 逐行解析
→ 错误定位
→ 批量入库
→ 异步处理
→ 结果报告
```

### 11.3 自动对账模型

- 精确匹配；
- 允许范围内匹配；
- 未匹配；
- 重复交易；
- 可疑交易；
- 人工确认。

### 11.4 工作流和状态控制

- 非法状态跳转；
- 并发审核；
- 失败任务；
- 可恢复性；
- 状态变更审计。

### 11.5 可追溯性

系统需要回答：

- 谁执行了操作；
- 何时执行；
- 修改前后状态；
- 为什么判定异常；
- 失败后如何处理。

### 11.6 哪些额外内容不能删

- CSV 导入流水线；
- 自动对账；
- 金额正确性；
- 简化状态流转；
- 多层幂等；
- 关键审计日志。

全部删除后，项目会退化为普通账户和交易 CRUD，显著降低简历辨识度。

### 11.7 哪些可以简化或删除

- 风险规则保留 2～3 条，不做规则平台；
- 审计只覆盖关键操作；
- 固定人民币和单一时区；
- 不做多币种；
- 不做复杂模糊对账；
- MinIO、前端和 Elasticsearch 都可以删除；最小 Prometheus/Grafana 监控已经提升为 P1，不能再作为范围压缩项。

---

## 12. 每周配套微实验

不要把所有缺失知识塞进 FinGuard；用每周 2～3 小时的小实验补齐。

1. JUC：线程安全、线程池、锁、竞态条件；
2. JVM：制造 OOM、死锁，查看 GC 日志，使用 `jstack`/`jcmd`；
3. MySQL：隔离级别、锁、MVCC、`EXPLAIN`；
4. 网络：DNS、TCP、TLS、HTTP 抓包或命令行观察；
5. Linux：进程、端口、日志、Docker 网络和故障排查；
6. 安全与工程：越权、JWT 过期、文件上传攻击和 GitHub Actions。

---

## 13. 安全和性能最低要求

### 13.1 安全检查

- SQL 注入；
- 登录暴力尝试；
- 水平越权；
- 垂直越权；
- JWT 过期和伪造；
- CSV 文件类型和大小限制；
- 密码加密；
- 日志敏感信息泄漏；
- CORS 配置；
- 审核接口重复提交。

### 13.2 性能检查

- 使用 `EXPLAIN` 分析关键查询；
- 检查分页和联合索引；
- 检查 Redis 缓存命中和失效；
- 检查线程池和 MQ 消费并发；
- 使用 JMeter 获取真实吞吐量、错误率、P95、P99；
- 记录测试环境、数据规模和参数；
- 只报告实际测得的数据；
- 不将本机压测结果描述为生产级高并发能力。

### 13.3 最小监控要求

- 使用 Actuator 暴露健康状态；
- 使用 Micrometer 输出 HTTP、JVM、数据库连接池和自定义业务指标；
- 使用 Prometheus 采集指标；
- 建立一个 Grafana 仪表盘；
- 至少展示请求延迟、JVM 内存、CSV 导入成功/失败数、对账耗时和消息失败数；
- 控制标签基数，禁止将用户 ID、交易 ID 等高基数字段作为指标标签；
- 不引入 OpenTelemetry、Collector 和分布式链路追踪。

### 13.4 最小 CI/CD 要求

GitHub Actions 至少完成：

```text
代码推送
→ Maven 编译
→ 自动测试
→ 打包
→ 构建 Docker 镜像
```

- 测试失败时流水线必须失败；
- 不把密码、Token、服务器密钥写入仓库；
- 如果拥有可用 Linux 服务器，可以增加手动触发的镜像发布和部署；
- 如果没有服务器，只表述为 CI 和镜像构建，不夸大为完整生产 CD。

### 13.5 Linux 部署与故障演练

必须完成一次真实 Linux 环境部署，并记录：

- Java、Docker、端口和环境变量；
- 应用启动、停止和重启；
- `ps`、`top`、`ss`、`curl`、`docker logs` 等排查命令；
- 日志、容器网络、数据卷和健康检查；
- 回滚或恢复步骤。

至少完成以下场景中的三项，并分别形成故障复盘：

1. 暂停 Redis，观察缓存降级或失败行为；
2. 让 RabbitMQ 消费者处理失败，观察重试和死信队列；
3. 删除或绕过关键索引，定位慢 SQL 并恢复；
4. 并发提交审核请求，观察乐观锁；
5. 配置错误端口或环境变量，使用 Linux 工具定位；
6. 制造线程池任务积压，观察响应和指标变化。

每份复盘必须包含：

```text
现象 → 假设 → 检查命令和证据 → 根因 → 修复 → 预防措施
```

---

## 14. 开发学习循环

每个功能必须使用下面的循环：

```text
1. 明确业务目标和验收标准
2. 讲解本阶段 Java / Spring / 数据库知识
3. 设计表结构、接口和关键流程
4. Codex 创建骨架和 TODO
5. 用户编写核心业务逻辑
6. Codex 进行 Code Review
7. 补充单元测试、集成测试和异常测试
8. 实际运行并排查错误
9. 提交 Git
10. 整理面试问题和技术取舍
```

用户应优先亲自编写：

- Service 业务逻辑；
- 事务边界；
- CSV 解析和校验；
- 对账逻辑；
- 状态流转；
- 幂等逻辑；
- MQ 消费者；
- 缓存一致性；
- 权限校验。

Codex 可以直接完成但必须解释：

- 基础配置；
- 重复 DTO/VO；
- Swagger 配置；
- 测试数据；
- Docker Compose 样板；
- CI 样板；
- 简单前端样板。

---

## 15. 六周完成标准

项目至少满足：

- 应用具有 Dockerfile，`docker compose up` 能启动应用及依赖；
- 项目已在真实 Linux 环境成功部署一次；
- Swagger 能走通完整业务流程；
- 同一 CSV 上传两次不会重复入库；
- 错误行能够定位，不破坏正确数据；
- MQ 消息重复投递不会产生重复交易；
- RabbitMQ 生产者确认、手动 ACK、重试和死信流程可验证；
- 对账能够产生匹配、未匹配、重复和可疑结果；
- 审核任务不能被重复处理；
- 关键操作存在审计记录；
- Redis 缓存能够正确失效；
- 核心 Service 有单元测试；
- 导入、对账、审核有集成测试；
- GitHub Actions 能自动编译、测试、打包和构建 Docker 镜像；
- Prometheus 能采集指标，Grafana 能展示系统和业务指标；
- 有一次真实压力测试和结果记录；
- 至少三次故障演练有可复现步骤和复盘记录；
- 安全测试清单已经执行并形成报告；
- README 包含架构、ER 图、启动方式和演示步骤；
- Git 历史能展示持续开发过程；
- 所有简历数据都可以复现或验证。

---

## 16. 最终交付物

```text
README.md
TASKS.md
pom.xml
Dockerfile
docker-compose.yml
.github/workflows/ci.yml
docs/PRD.md
docs/ARCHITECTURE.md
docs/DATABASE.md
docs/API.md
docs/DEPLOYMENT.md
docs/LEARNING_LOG.md
docs/INTERVIEW_NOTES.md
docs/PERFORMANCE_REPORT.md
docs/SECURITY_TEST_REPORT.md
docs/MONITORING.md
docs/runbooks/
docs/incidents/
docs/adr/
deploy/
monitoring/
sample-data/
src/main/
src/test/
```

最终还需要：

- GitHub 仓库；
- 连续、清晰的 Git 提交；
- Swagger 或 Postman 演示流程；
- 示例 CSV；
- ER 图；
- 项目截图或简短演示；
- 已知问题和故障复盘；
- 3～4 条可验证的简历描述。

---

## 17. 简历定位与示例

### 17.1 项目标题

**FinGuard Core 交易对账与异常审核平台｜个人项目**

### 17.2 技术栈

```text
Spring Boot、Spring Security、MyBatis-Plus、MySQL、
Redis、RabbitMQ、JUnit 5、Docker
```

### 17.3 简历表述草案

- 设计并实现交易流水导入、自动对账、异常审核和审计追踪闭环，支持 CSV 批量校验、错误定位和任务状态查询。
- 通过文件 SHA-256、数据库唯一索引和 MQ 消费者幂等机制，避免文件重复上传及消息重复消费造成的数据重复。
- 使用 RabbitMQ 异步执行导入与对账任务，配置消息重试和死信处理；使用 Redis 实现统计缓存和接口限流。
- 通过乐观锁控制审核状态并发更新，使用 JUnit、Mockito 和集成测试覆盖导入、对账与审核等核心流程。

压力测试完成后，才允许将真实数据补进简历。

禁止写：

- 百万用户；
- 亿级数据；
- 生产级高并发；
- 微服务实战；
- K8s 生产部署；
- 未测量的性能提升百分比。

---

## 18. 招聘环节与准备重点

| 招聘环节 | 主要决定因素 |
|---|---|
| 简历筛选 | 项目闭环、技术栈、项目辨识度、表达清晰度 |
| 笔试 | 数据结构与算法 |
| 基础面试 | Java、JVM、MySQL、Redis、网络、操作系统 |
| 项目面试 | 业务理解、幂等、事务、MQ、缓存、异常处理、测试 |
| 综合判断 | 学习能力、沟通、实习时间、可培养性 |

FinGuard 不能替代算法和基础知识。项目做得好但算法完全不会，仍然难以通过大厂笔试和技术面试。

---

## 19. 本地参考资料

以下是前期已有资料，可作为参考，但不能直接照搬为最终范围。

### 19.1 智能记账系统资料

```text
C:\Users\王俊凯\OneDrive\Desktop\计算机基础\学习执行包\项目-智能记账系统\README.md
C:\Users\王俊凯\OneDrive\Desktop\计算机基础\学习执行包\项目-智能记账系统\PRD.md
C:\Users\王俊凯\OneDrive\Desktop\计算机基础\学习执行包\项目-智能记账系统\API.md
C:\Users\王俊凯\OneDrive\Desktop\计算机基础\学习执行包\项目-智能记账系统\schema.sql
C:\Users\王俊凯\OneDrive\Desktop\计算机基础\学习执行包\项目-智能记账系统\docker-compose.yml
```

智能记账系统原有模块包括用户、角色、认证、账户、分类、交易、预算、统计、CSV、审计和可选 Agent。FinGuard 可以借鉴基础设计，但不直接在原目录上堆叠功能。

### 19.2 早期 FinGuard 企业版架构参考

```text
C:\Users\王俊凯\OneDrive\Desktop\计算机基础\.superpowers\brainstorm\20260624-182307-7492\content\01-architecture-overview.html
```

该文件属于早期企业级完整版思路，包含更多中间件和长期演进内容。只能参考业务和架构思想，不能作为当前 6 周实现清单。

### 19.3 学习路径参考

- 黑马 Java 学习路线：用于查漏补缺；
- 黑马 AI 应用开发路线：https://www.bilibili.com/opus/1156896913511940102
- Spring AI Alibaba：https://github.com/alibaba/spring-ai-alibaba
- Spring AI Alibaba Examples：https://github.com/spring-ai-alibaba/examples

AI 资料只用于 FinGuard Copilot 长期阶段，不进入当前 Core 主线。

---

## 20. 项目目录与当前状态

推荐将 Codex 项目、Java 项目和 Git 仓库根目录保持一致：

```text
C:\dev\finguard-core
```

不要将代码仓库放在 OneDrive 中文路径下，以减少同步占用、文件监听、脚本和容器路径问题。学习笔记仍可保留在“计算机基础”项目中。

截至 2026-07-17 的最后检查：

- `C:\dev\finguard-core` 已创建；
- Git 仓库已初始化；
- `PROJECT_BRIEF.md` 已放入项目根目录但尚未提交；
- 尚无 `pom.xml`；
- 尚未生成 Spring Boot 骨架。

历史环境记录中 Git 已可用，而 Java 和 Maven 当时尚未配置；该记录可能过时，因此阶段 0 必须重新检测 JDK、Maven、Git 和 Docker。

---

## 21. 新任务启动提示词

在新建的 FinGuard Core Codex 项目中，先让 Codex 阅读本文，然后使用下面的提示词启动：

```text
请先完整阅读项目根目录中的 `PROJECT_BRIEF.md`，并将其作为当前项目范围和学习方式的唯一事实来源。

我要在 6 周内完成 FinGuard Core 实习版，每天可以投入 8 小时以上。目标是达到第一段 Java 后端实习的项目标准，而不是成为高级程序员，也不是覆盖所有企业级技术。

工作规则：
1. 不要一次性生成完整项目。
2. 每次只完成一个可运行、可测试、可提交的小里程碑。
3. 编码前讲清业务目标、知识点、设计选择、修改文件和验收标准。
4. 核心业务代码优先让我编写，你提供骨架、TODO、提示和 Code Review。
5. 样板代码可以由你实现，但必须解释用途。
6. 每个功能必须实际运行验证并形成 Git 提交。
7. 优先完成 P0，再完成 P1；不得提前引入交接文档中排除的技术。
8. 每周检查进度，落后时主动删除 P2。
9. 同步整理面试问题、学习笔记和简历表述。
10. 禁止虚构性能数据。

现在只执行阶段 0：
1. 检查当前目录和已有文件；
2. 检查 JDK 17、Maven、Git、Docker 环境；
3. 制定第 1 周任务清单和今天的最小任务；
4. 确认 Git 仓库状态并准备第一次提交；
5. 创建最小 Spring Boot 骨架、健康检查和第一条测试；
6. 不要生成完整业务模块。
```

---

## 22. 阶段 0 的验收标准

阶段 0 只完成以下内容：

- 确认 JDK 17；
- 确认 Maven；
- 确认 Git；
- 确认 Docker；
- 确认 Git 仓库已初始化；
- 创建 Spring Boot 最小项目；
- 应用能够启动；
- 健康检查接口能够访问；
- 第一条测试通过；
- 建立 README 和 TASKS；
- 将 PROJECT_BRIEF、README、TASKS 和最小项目骨架纳入第一次 Git 提交。

阶段 0 不允许：

- 一次生成所有数据库表；
- 一次生成全部 Controller/Service/Mapper；
- 提前加入 Redis、RabbitMQ、MinIO、Elasticsearch；
- 提前拆微服务；
- 一次性生成整个六周代码。

---

## 23. 六周后的长期路线

FinGuard Core 完成并开始投递后，再按岗位需求选择：

1. 深化算法、JVM、JUC、MySQL、Redis、网络和操作系统；
2. 根据目标 JD 增加 Elasticsearch、微服务或 Kubernetes 专项实验；
3. 单独新建 `FinGuard Copilot`；
4. 使用 Spring AI Alibaba、RAG、Tool Calling、MCP 和评估体系开发 AI 应用；
5. Core 与 Copilot 通过 API、MQ 或 MCP 连接，而不是揉成一个无法按时完成的大项目。

---

## 24. 最终一句话

> 用 6 周完成一个模块化单体 FinGuard Core，以 CSV 异步导入、自动对账、多层幂等、异常审核和审计为核心；项目、算法和基础知识三线并行，第 4 周开始投递，完成后再做 AI Agent 或微服务扩展。
