# FinGuard Core 任务清单

> 本文只记录当前进度、每个 Day 的必要任务、关键文件和验收结论。
> 项目范围以 [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) 为准，详细历史证据放在 `docs/` 和 Git 提交中。

## 1. 使用规则

- 每次只推进一个可运行、可测试、可提交的小里程碑。
- 每个 Day 统一使用：状态、目标、任务、关键文件、验收、提交。
- 已完成 Day 只保留最终结论，不在本文重复调试过程和知识讲解。
- 未开始 Day 只锁定当前里程碑；后续任务在进入当天时再细化。
- 已应用的 Flyway 迁移不得修改，数据库变更必须新增迁移。
- 完成实现后必须运行相关测试和完整 `mvn clean test`；数据库功能还要完成真实 MySQL/HTTP 验收和数据清理。

## 2. 当前进度

| 阶段 | 状态 | 结论 |
|---|---|---|
| 阶段 0 | 已完成 | 工程、Git、Spring Boot、Docker 和健康检查基线可用 |
| Week 1 | 已完成 | 账户、人工交易、分页、统一错误处理和周验收完成 |
| Week 2 | 已完成 | 登录、JWT、RBAC、事务、索引和综合验收完成 |
| Week 3 Day 1 | 已完成 | CSV 文件、字段、幂等、失败、状态和测试契约已锁定 |
| Week 3 Day 2 | 已完成 | 导入任务与行错误的数据模型、V4 迁移、Mapper 和真实数据库验收完成 |
| Week 3 Day 3 | 已完成 | 原始文件校验、SHA-256 指纹、CSV 结构解析和边界测试完成 |
| Week 3 Day 4 | 已完成 | 逐行规范化、批量账户解析、文件内/数据库重复判断和真实 MySQL 验收完成 |
| Week 3 Day 5 | 已完成 | 同步上传、文件哈希幂等、状态流转、批量持久化、失败恢复和权限验收完成 |
| Week 3 Day 6 | 已完成 | 同步自动对账、四类结果、幂等、批处理、失败恢复和权限验收完成 |
| Week 3 Day 7 | 已完成 | Week 3 综合验收、真实 MySQL/JWT/HTTP 验收、清理和周复盘完成 |
| Week 4 Day 1 | 已完成 | 异步导入/对账消息契约、文件持久化方案、Outbox、Confirm/ACK、幂等、重试和死信边界已锁定 |
| Week 4 Day 2 | 已完成 | RabbitMQ 依赖、Docker 服务、连接配置、导入/对账基础拓扑、真实连接和健康验收完成 |
| Week 4 Day 3 | 已完成 | V7、原始文件持久化、Outbox、202 异步受理、Confirm/return 与发布退避已完成真实验收 |
| Week 4 Day 4 | 已完成 | 异步导入消费者、数据库业务事务、提交后手动 ACK 和真实验收完成 |
| Week 4 Day 5 | 已完成 | 导入消费幂等、任务行锁、ACK 丢失红投和消费者崩溃恢复已完成真实验收 |
| Week 4 Day 6 | 已完成 | 异步对账消费者、两级有限重试、失败分类、DLQ 隔离和真实验收完成 |
| Week 4 Day 7 | 已完成 | 综合验收真实异步闭环、可靠性故障路径、幂等、清理并完成周复盘 |
| Week 5 Day 1 | 已完成 | 风险、审核、Redis、审计契约与 Day 2～Day 6 实现边界已锁定 |
| Week 5 Day 2 | 已完成 | V8 风险命中真源、批量持久层、规则契约和真实验收完成 |
| Week 5 Day 3 | 已完成 | 三条风险规则、V9 审核任务真源、任务生成与对账事务接入已完成真实验收 |
| Week 5 Day 4 | 已完成 | 审核查询/决策接口、条件更新乐观锁、并发冲突和权限矩阵已完成真实验收 |
| Week 5 Day 5 | 已完成 | V10 审计真源、五类白名单事件、同事务回滚、ADMIN 查询与真实安全验收完成 |
| Week 5 Day 6 | 已完成 | Redis 统计缓存、提交后失效、登录/上传固定窗口限流与故障降级已完成真实验收 |
| Week 6 | 待规划 | CI/CD、Linux 部署、监控、压测、安全测试和项目收尾 |

## 3. 阶段 0：工程基线

### Day 0：项目初始化

- **状态**：已完成
- **目标**：建立可编译、可启动、可测试的 Spring Boot 工程。
- **任务**：
  - [x] 确认 Java 17、Maven、Git、Docker 和 Docker Compose 可用。
  - [x] 创建 Spring Boot、Maven 和 Git 基线。
  - [x] 暴露并验证 `/actuator/health`。
  - [x] 建立 README、任务清单和首个自动化测试。
- **关键文件**：
  - `pom.xml`
  - `src/main/java/com/finguard/core/FinGuardCoreApplication.java`
  - `src/main/resources/application.yml`
  - `src/test/java/com/finguard/core/HealthEndpointIntegrationTest.java`
- **验收**：应用可启动，健康状态为 `UP`，基线测试通过。
- **提交**：`f740ceb chore: initialize Spring Boot project`；`b422d7d docs: complete phase 0 environment setup`

## 4. Week 1：账户与人工交易

### Day 1：环境与最小骨架

- **状态**：已完成
- **目标**：理解工程结构、启动流程和基本开发闭环。
- **任务**：
  - [x] 复核阶段 0 环境与工程基线。
  - [x] 理解 Maven 生命周期和 Spring Boot 启动流程。
  - [x] 确认健康检查、测试和 Git 提交流程可重复。
- **关键文件**：
  - `pom.xml`
  - `src/main/java/com/finguard/core/FinGuardCoreApplication.java`
  - `src/test/java/com/finguard/core/HealthEndpointIntegrationTest.java`
- **验收**：最小应用可重复编译、测试和启动。
- **提交**：沿用阶段 0 基线提交。

### Day 2：需求与数据模型

- **状态**：已完成
- **目标**：锁定账户、交易的最小业务规则和数据库模型。
- **任务**：
  - [x] 定义账户与交易字段、状态、枚举和一对多关系。
  - [x] 明确金额精度、软删除和交易业务唯一键。
  - [x] 绘制 ER 图并完成设计评审。
  - [x] 不提前创建后续对账、审核和审计表。
- **关键文件**：
  - `docs/design/day2-requirements-and-data-model.md`
- **验收**：设计覆盖账户、交易、唯一约束和后续扩展边界。
- **提交**：`297abd0 docs: define account and transaction data model`

### Day 3：MySQL 与 Flyway 基线

- **状态**：已完成
- **目标**：建立真实 MySQL、数据源、MyBatis-Plus 和 Flyway 基线。
- **任务**：
  - [x] 使用 Docker Compose 启动 MySQL。
  - [x] 配置 DataSource、MyBatis-Plus 和 Flyway。
  - [x] 新增 V1，创建 `accounts` 和 `transactions`。
  - [x] 验证迁移、约束和数据库连接。
- **关键文件**：
  - `docker-compose.yml`
  - `src/main/resources/application.yml`
  - `src/main/resources/db/migration/V1__create_account_and_transaction_tables.sql`
  - `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`
- **验收**：MySQL 健康，Flyway V1 可从空库执行，数据库集成测试通过。
- **提交**：`3b3b33b chore: establish MySQL and Flyway baseline`

### Day 4：账户 CRUD

- **状态**：已完成
- **目标**：完成账户创建、查询、改名、状态切换和软删除。
- **任务**：
  - [x] 建立账户 Controller、DTO/VO、Service、Entity 和 Mapper。
  - [x] 实现账户号标准化、参数校验和双层唯一性保护。
  - [x] 限制只有无交易历史的禁用账户可删除。
  - [x] 补充 Service、Controller 和真实 MySQL 验收。
- **关键文件**：
  - `src/main/java/com/finguard/core/account/`
  - `src/test/java/com/finguard/core/account/`
- **验收**：26 项测试通过；真实 HTTP/MySQL 创建、查询、修改、禁用和删除流程通过。
- **提交**：`d118d98 feat: implement account CRUD`

### Day 5：人工交易 CRUD

- **状态**：已完成
- **目标**：完成 `MANUAL` 交易的创建、查询、修改和软删除。
- **任务**：
  - [x] 建立交易 Controller、DTO/VO、Service、Entity 和 Mapper。
  - [x] 实现金额精度、业务时间、来源和描述校验。
  - [x] 实现业务键预查重和数据库唯一约束兜底。
  - [x] 限制禁用账户和非 `MANUAL` 交易的非法操作。
- **关键文件**：
  - `src/main/java/com/finguard/core/transaction/`
  - `src/test/java/com/finguard/core/transaction/`
- **验收**：66 项测试通过；真实 HTTP/MySQL CRUD、幂等删除和失败不落库验证通过。
- **提交**：`43d3724 feat: implement transaction CRUD`

### Day 6：分页、筛选与统一错误

- **状态**：已完成
- **目标**：补齐查询能力和统一 REST 错误契约。
- **任务**：
  - [x] 实现账户、交易分页、组合筛选和稳定排序。
  - [x] 限制分页参数并统一分页响应。
  - [x] 实现全局异常处理和 `400/404/409/500` 映射。
  - [x] 补充 Controller、Service 和真实数据库集成测试。
- **关键文件**：
  - `src/main/java/com/finguard/core/common/`
  - `src/main/java/com/finguard/core/config/MybatisPlusConfiguration.java`
  - `src/test/java/com/finguard/core/PaginationQueryIntegrationTest.java`
  - `src/test/java/com/finguard/core/GlobalExceptionHandlerIntegrationTest.java`
- **验收**：86 项测试通过；分页、参数错误、资源不存在、重复和非法状态流程通过。
- **提交**：`81a466f feat: add paginated queries and global error handling`

### Day 7：Week 1 验收与复盘

- **状态**：已完成
- **目标**：确认 Week 1 能从干净环境重建、运行、测试和解释。
- **任务**：
  - [x] 复核账户、交易、分页、错误处理和数据库约束。
  - [x] 运行完整测试和真实 HTTP/MySQL 验收。
  - [x] 清理验收数据并检查 Git 历史。
  - [x] 整理常见错误、知识点和面试问题。
- **关键文件**：
  - `docs/review/week1-review.md`
- **验收**：Week 1 功能无回归，验收数据和端口已清理，提交历史连续可解释。
- **提交**：`83ae373 docs: add week 1 acceptance review`；`3c3043e docs: complete week 1 acceptance`

## 5. Week 2：认证、授权、事务与索引

### Day 1：认证方案与权限矩阵

- **状态**：已完成
- **目标**：先锁定登录、JWT、RBAC、数据库模型和测试边界。
- **任务**：
  - [x] 定义登录请求、响应、失败状态和 JWT Claims。
  - [x] 定义 ADMIN、REVIEWER 和匿名用户权限矩阵。
  - [x] 设计 `users`、`roles`、`user_roles`。
  - [x] 明确本周不实现刷新令牌、OAuth、Redis 限流和前端。
- **关键文件**：
  - `docs/design/week2-day1-authentication-design.md`
- **验收**：设计覆盖全部现有路由、认证数据模型和 Day 2～Day 5 实现顺序。
- **提交**：`0afaaba docs: design authentication and authorization`

### Day 2：认证持久层

- **状态**：已完成
- **目标**：建立用户、角色和用户角色关系的数据库与 Mapper 基线。
- **任务**：
  - [x] 新增 V2，创建认证三表并初始化固定角色。
  - [x] 建立 Entity、枚举、Mapper 和认证查询模型。
  - [x] 实现按规范化用户名加载用户及角色。
  - [x] 验证唯一约束、外键、角色查询和空库迁移。
- **关键文件**：
  - `src/main/resources/db/migration/V2__create_auth_tables.sql`
  - `src/main/java/com/finguard/core/auth/entity/`
  - `src/main/java/com/finguard/core/auth/mapper/`
  - `src/test/java/com/finguard/core/auth/AuthDatabaseIntegrationTest.java`
  - `src/test/java/com/finguard/core/auth/mapper/AuthMapperIntegrationTest.java`
- **验收**：独立空库可执行 V1→V2；认证持久层、约束和完整回归测试通过。
- **提交**：`2666a00 feat: add authentication persistence layer`

### Day 3：登录与 JWT 签发

- **状态**：已完成
- **目标**：完成用户名密码认证和可真实验签的 JWT Access Token。
- **任务**：
  - [x] 配置 BCrypt、AuthenticationManager、JWT Encoder/Decoder 和密钥校验。
  - [x] 实现登录 DTO、Controller、Service 和数据库用户加载。
  - [x] 统一不存在用户、错误密码和禁用用户的 `401`。
  - [x] 实现默认关闭、凭据外置、密码哈希入库的本地初始化器。
- **关键文件**：
  - `src/main/java/com/finguard/core/auth/controller/AuthController.java`
  - `src/main/java/com/finguard/core/auth/service/`
  - `src/main/java/com/finguard/core/auth/security/`
  - `src/main/java/com/finguard/core/auth/bootstrap/`
  - `src/main/java/com/finguard/core/auth/config/`
- **验收**：正确登录返回两小时 JWT；失败路径一致；Token 可真实验签且不含敏感信息。
- **提交**：`7d14297 feat: implement login and JWT issuance`

### Day 4：无状态 JWT 入站认证

- **状态**：已完成
- **目标**：使用 Spring Security Resource Server 校验 Bearer Token。
- **任务**：
  - [x] 配置无状态 Security Filter Chain。
  - [x] 匿名放行登录和健康检查，其余业务接口要求合法 JWT。
  - [x] 校验签名、issuer、格式和过期时间。
  - [x] 将角色 Claims 转为 Spring Security Authorities。
  - [x] 统一无 Token、错误 Token 和过期 Token 的 `401`。
- **关键文件**：
  - `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`
  - `src/main/java/com/finguard/core/auth/security/JwtRoleConverter.java`
  - `src/main/java/com/finguard/core/auth/security/RestAuthenticationEntryPoint.java`
  - `src/test/java/com/finguard/core/auth/security/JwtAuthenticationIntegrationTest.java`
- **验收**：合法 Token 建立身份；匿名、格式错误、篡改和过期 Token 均按契约拒绝。
- **提交**：`33ca50c feat: add stateless JWT authentication`

### Day 5：RBAC 与业务接口授权

- **状态**：已完成
- **目标**：按权限矩阵保护账户和交易接口。
- **任务**：
  - [x] ADMIN 获得账户、交易全部读写权限。
  - [x] REVIEWER 只获得账户、交易查询权限。
  - [x] 区分并统一 `401` 与 `403` 响应。
  - [x] 覆盖匿名、无角色、ADMIN 和 REVIEWER 权限矩阵。
- **关键文件**：
  - `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`
  - `src/main/java/com/finguard/core/auth/security/RestAccessDeniedHandler.java`
  - `src/test/java/com/finguard/core/auth/security/RbacAuthorizationIntegrationTest.java`
- **验收**：155 项测试通过；真实 ADMIN 读写、REVIEWER 只读和越权不改库验证通过。
- **提交**：`573069b feat: enforce RBAC for business endpoints`

### Day 6：事务边界与索引优化

- **状态**：已完成
- **目标**：验证 Service 事务、回滚行为和关键查询执行计划。
- **任务**：
  - [x] 审计账户、交易、认证的读写事务边界。
  - [x] 用多表写入微实验验证运行时异常整体回滚。
  - [x] 使用代表性数据执行用户名、角色和交易分页 `EXPLAIN`。
  - [x] 新增 V3，优化默认交易分页并保留原按账户索引。
- **关键文件**：
  - `src/main/resources/db/migration/V3__add_transaction_pagination_index.sql`
  - `src/test/java/com/finguard/core/auth/TransactionBoundaryIntegrationTest.java`
  - `docs/analysis/week2-day6-transaction-and-index-analysis.md`
- **验收**：157 项测试通过；空库 V1→V3、事务回滚和关键执行计划验证通过。
- **提交**：`dbe799d perf: verify transactions and optimize query indexes`

### Day 7：Week 2 综合验收

- **状态**：已完成
- **目标**：证明登录→JWT→RBAC 闭环可从干净环境稳定重建和运行。
- **任务**：
  - [x] 验证空库 V1→V3、五张核心表、约束和索引。
  - [x] 运行完整自动化回归。
  - [x] 完成真实登录、ADMIN、REVIEWER、匿名和失败路径验收。
  - [x] 复核事务回滚和 `EXPLAIN` 证据。
  - [x] 清理账户、交易、用户、角色绑定、临时库、凭据和端口。
- **关键文件**：
  - `docs/review/week2-review.md`
- **验收**：157 项测试通过；真实应用为 `UP`；授权、回滚、索引和清理全部通过。
- **提交**：`7febe0d docs: complete week 2 acceptance review`

## 6. Week 3 当前执行

### Week 3 Day 1：CSV 导入需求与契约设计

- **状态**：已完成
- **目标**：锁定 CSV 文件、字段、幂等和失败处理契约，不提前实现异步消息或自动对账。
- **任务**：
  - [x] 定义文件编码、表头、字段顺序、大小和行数限制。
  - [x] 定义账户、流水号、方向、金额、时间和描述的字段映射。
  - [x] 定义文件 SHA-256 幂等键和交易业务唯一键的职责边界。
  - [x] 定义整文件失败、错误行隔离、重复行和部分成功策略。
  - [x] 定义导入任务状态、错误摘要和验收测试清单。
  - [x] 明确 Day 1 只产出设计，不实现 RabbitMQ、Redis 或自动对账。
- **关键文件**：
  - `docs/design/week3-day1-csv-import-contract.md`
  - `TASKS.md`
- **验收**：
  - [x] 文件契约、示例、错误场景和幂等边界可直接指导后续实现。
  - [x] 设计与现有 `transactions` 唯一约束兼容。
  - [x] `mvn clean test` 全部通过，`git diff --check` 通过。
  - [x] Day 1 新增改动仅包含设计文档和任务入口。
- **提交**：`docs: design CSV import contract`

### Week 3 Day 2：导入任务数据模型与持久层

- **状态**：已完成
- **业务目标**：给每次被系统接受的 CSV 建立可追踪的任务账本，并把可分页定位的行错误持久化，为后续同步上传、解析和交易入库提供可靠落点。
- **范围边界**：只完成数据库模型、Java 持久层和真实 MySQL 集成测试；不实现上传接口、SHA-256 计算、CSV 解析、交易入库、状态流转 Service、RabbitMQ、Redis 或自动对账。
- **执行顺序**：
  1. 先评审字段、状态、错误码、关系和查询路径，产出 Day 2 数据模型设计。
  2. 新增 V4 Flyway 迁移，用数据库约束兜住文件哈希幂等和数据合法性。
  3. 建立 `importjob` 模块的枚举、实体和 Mapper。
  4. 用 Mapper 集成测试验证写入、查询、唯一约束、外键和稳定分页。
  5. 执行空库迁移、完整测试、真实 SQL 检查和范围审计。
- **任务**：
  - [x] 设计 `import_jobs`：包含原文件名、64 位小写 SHA-256、文件大小、状态、四项行数统计、文件错误、错误摘要、创建人和处理时间。
  - [x] 设计 `import_row_errors`：包含任务 ID、CSV 逻辑记录号、字段名、稳定错误码、截断后的拒绝值、安全消息和创建时间。
  - [x] 明确 `users → import_jobs → import_row_errors` 的一对多关系；两个外键均使用 `ON DELETE RESTRICT`，避免删除任务追踪证据。
  - [x] 明确数据库约束：文件哈希唯一且格式合法、文件大小为 1～5 MiB、状态/错误码属于允许集合、统计均非负、`duplicate_rows <= failed_rows`。
  - [x] 为 `import_row_errors` 建立 `(import_job_id, csv_row_number, id)` 联合索引，映射业务字段 `rowNumber` 并支持稳定分页；不使用 MySQL 关键字 `row_number`，也不添加没有查询依据的索引。
  - [x] 新增 `V4__create_import_job_tables.sql`；保持 V1～V3 不变，并同步更新所有“最新 Flyway 版本”和业务表数量断言。
  - [x] 新增 `ImportJobStatus`、`ImportFileErrorCode`、`ImportRowErrorCode`，与 Day 1 契约中的状态和错误码逐项一致。
  - [x] 新增 `ImportJob`、`ImportRowError` 实体及 `ImportJobMapper`、`ImportRowErrorMapper`。
  - [x] Mapper 支持按 ID 查询任务、按文件哈希查找原任务，以及按任务 ID 稳定分页查询行错误；Day 2 不加入 Controller 或 Service。
  - [x] 新增独立的导入持久层测试夹具，按“行错误 → 导入任务 → 用户”的顺序清理测试数据，不修改 Flyway 固定角色。
  - [x] 集成测试覆盖任务/错误往返持久化、五种状态、文件级/行级错误码、唯一哈希冲突、非法值、未知外键、删除限制和多页稳定排序。
- **关键文件**：
  - `docs/design/week3-day2-import-persistence-design.md`
  - `src/main/resources/db/migration/V4__create_import_job_tables.sql`
  - `src/main/java/com/finguard/core/importjob/model/ImportJobStatus.java`
  - `src/main/java/com/finguard/core/importjob/model/ImportFileErrorCode.java`
  - `src/main/java/com/finguard/core/importjob/model/ImportRowErrorCode.java`
  - `src/main/java/com/finguard/core/importjob/entity/ImportJob.java`
  - `src/main/java/com/finguard/core/importjob/entity/ImportRowError.java`
  - `src/main/java/com/finguard/core/importjob/mapper/ImportJobMapper.java`
  - `src/main/java/com/finguard/core/importjob/mapper/ImportRowErrorMapper.java`
  - `src/test/java/com/finguard/core/importjob/ImportJobPersistenceIntegrationTest.java`
  - `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`
- **验收**：
  - [x] `docker compose ps` 显示 MySQL 为 `healthy`。
  - [x] 从空数据卷执行 V1→V4 迁移成功，共存在 7 张当前业务表，V1～V3 校验和不变。
  - [x] `information_schema` 证明两张新表、文件哈希唯一约束、两个 `RESTRICT` 外键、13 个检查约束和错误分页联合索引真实存在。
  - [x] 相同文件哈希只能创建一个任务；非法状态、错误码、统计、文件大小和外键数据均被数据库拒绝。
  - [x] Mapper 能按哈希返回原任务，行错误跨页查询无重复、无遗漏且顺序稳定。
  - [x] Day 2 聚焦测试 12/12 通过，`mvn clean test` 164/164 通过且无跳过；测试数据清理为 0。
  - [x] 应用健康检查为 `UP`，端口 8080 已释放，`git diff --check` 通过。
  - [x] 变更范围中没有上传端点、CSV 解析依赖、交易写入、消息队列、Redis、对账或对 V1～V3 的修改。
- **验收结论**：真实 MySQL `EXPLAIN` 使用 `idx_import_row_errors_job_row_id`，错误顺序为 `csv_row_number ASC, id ASC`；项目专属数据卷已重建并完成空库验收。
- **提交**：`feat: add import job persistence layer`

### Week 3 Day 3：CSV 文件指纹与结构解析

- **状态**：已完成
- **业务目标**：把一份上传文件安全、确定地转换为“文件元数据 + SHA-256 指纹 + 带逻辑记录号的原始 CSV 行”，为 Day 4 逐字段业务校验提供不依赖 HTTP、数据库和消息队列的解析基础。
- **范围边界**：
  - 本日只处理文件名、字节大小、UTF-8/BOM、换行、CSV 语法、固定表头、数据记录数、原始字节 SHA-256 和逻辑记录号。
  - 本日不开放上传 Controller，不创建或更新 `import_jobs`，不查询账户，不校验金额/方向/时间等业务字段，不写入 `transactions`，不实现任务状态流转、RabbitMQ、Redis 或自动对账。
  - 解析结果保留六个字段的原始文本；规范化、业务校验、重复交易判断和入库留给后续 Day。
- **执行顺序**：
  1. 先从 Day 1 契约提取文件级规则、错误层级和解析结果模型，完成 Day 3 设计评审。
  2. 引入成熟 CSV 解析库并锁定配置，禁止使用 `String.split(",")`。
  3. 实现请求级文件检查与原始字节 SHA-256，确保哈希发生在去 BOM、解码、换行处理和字段修剪之前。
  4. 实现严格 UTF-8 解码、BOM/换行检查、固定表头校验和 CSV 逻辑记录解析。
  5. 用纯单元测试覆盖成功、边界和失败场景，再运行完整回归与范围审计。
- **任务**：
  - [x] 新增 Day 3 设计文档，明确组件职责、调用顺序、异常类型、解析结果和 Day 4 交接边界。
  - [x] 在 `pom.xml` 增加成熟 CSV 解析依赖；配置为保留空白记录、识别引号字段和转义双引号，不自行拆分字符串。
  - [x] 实现请求级检查：原文件名存在、扩展名为 `.csv`（大小写不敏感）、文件非空且不超过 5 MiB；失败时不产生可持久化任务结果。
  - [x] 实现 SHA-256 文件指纹：直接对原始字节计算，输出固定 64 位小写十六进制；BOM、换行或空白变化应产生不同指纹。
  - [x] 使用严格 UTF-8 解码，允许且只允许文件开头存在一个 UTF-8 BOM；拒绝非法字节、错误位置 BOM 和裸 `CR` 换行。
  - [x] 严格校验固定表头的名称、大小写、顺序、数量和重复情况，并将失败稳定映射为 `INVALID_HEADER`。
  - [x] 使用 CSV 库解析逗号、引号、转义引号和引号内换行；结构损坏稳定映射为 `MALFORMED_CSV`，不暴露解析器内部异常。
  - [x] 保留数据区空白记录，不静默跳过；把每条记录转换为包含 CSV 逻辑记录号和原始字段列表的不可变解析对象。
  - [x] 校验数据记录数为 1～10,000；分别映射 `NO_DATA_ROWS` 和 `TOO_MANY_ROWS`，超过上限时尽早停止解析。
  - [x] 区分请求级拒绝与文件级解析失败：前者供后续 HTTP `400/413` 使用，后者携带现有 `ImportFileErrorCode`，供后续任务进入 `FAILED`。
  - [x] 单元测试覆盖：UTF-8 BOM、LF/CRLF、逗号描述、转义引号、多行描述、空白记录、同字节哈希、字节变化哈希、非法 UTF-8、裸 `CR`、错误表头、未闭合引号、零数据行和 10,001 行。
- **关键文件**：
  - `docs/design/week3-day3-csv-file-parsing-design.md`
  - `pom.xml`
  - `src/main/java/com/finguard/core/importjob/parser/`
  - `src/main/java/com/finguard/core/importjob/exception/`
  - `src/test/java/com/finguard/core/importjob/parser/`
- **验收**：
  - [x] 合法样例可得到 64 位小写 SHA-256、固定六列原始值和正确的 CSV 逻辑记录号。
  - [x] 带逗号、引号或换行的合法描述不会被错误拆行；空白记录会被保留给 Day 4 形成行错误。
  - [x] 所有请求级和文件级失败均按 Day 1 契约分类，异常对象和解析结果中不出现服务器路径、原始堆栈或第三方解析器细节。
  - [x] 10,000 条数据记录通过，10,001 条在不继续无界读取的情况下返回 `TOO_MANY_ROWS`。
  - [x] Day 3 聚焦测试 14/14 通过，`mvn clean test` 178/178 通过且无跳过，`git diff --check` 通过。
  - [x] 应用健康检查为 `UP`，端口 8080 已释放，临时日志已清理。
  - [x] 变更中没有 Controller、V5 迁移、任务状态 Service、账户/交易 Mapper 调用、数据库写入、RabbitMQ、Redis 或对账实现。
- **验收结论**：Apache Commons CSV 1.14.1 依赖解析正确；合法与失败边界、5 MiB 文件上限和 10,000 行上限均由自动化测试覆盖。
- **提交**：`feat: add CSV file parsing foundation`

### Week 3 Day 4：CSV 逐行规范化与业务校验

- **状态**：已完成
- **业务目标**：把 Day 3 产生的原始 `ParsedCsvRow` 转换为“可供后续入库的规范化交易候选 + 可持久化的稳定行错误”，并在不写数据库的前提下完成账户解析、文件内重复和数据库重复判断。
- **请求流**：

  ```text
  ParsedImportFile
    → 校验每条记录恰好六列
    → 修剪、规范化并校验六个字段
    → 批量查询账户并解析 accountId
    → 按 CSV 逻辑记录顺序识别文件内重复
    → 分批查询数据库中已有的 CSV_IMPORT 业务键
    → 输出合法交易候选和行错误
  ```

- **范围边界**：
  - 本日只读取账户和现有交易，不创建或更新 `import_jobs`、`import_row_errors`、`transactions`。
  - 本日不开放上传或查询 Controller，不处理 multipart、HTTP 状态、JWT 当前用户或权限规则。
  - 本日不负责 `PENDING → PROCESSING → 终态`、统计汇总、事务编排、批量插入、数据库唯一键冲突兜底和系统失败恢复；这些留给 Day 5。
  - 不重新读取原始文件、不重新计算 SHA-256，也不重新解释 UTF-8、BOM、换行、CSV 引号或逻辑记录边界。
  - 不新增 RabbitMQ、Redis、自动对账、风险、审核或审计功能；没有真实 SQL 证据时不新增 V5 或索引。
- **关键设计决定**：
  - 列数不是六列时只生成 `COLUMN_COUNT_MISMATCH`，不再按下标读取字段；空白记录也按该规则处理。
  - 字段按 `account_no → external_transaction_no → direction → amount → transaction_time → description` 的固定顺序处理；同一记录可收集多个互不依赖的字段错误，但失败记录只计一次。
  - 账户号使用 `trim + Locale.ROOT 大写`；流水号只 `trim` 且保持大小写；方向使用 `trim + Locale.ROOT 大写`；描述 `trim` 后空串转 `null`。
  - 金额只接受 Day 1 锁定的十进制文本格式，转换为两位小数时禁止舍入；时间使用严格 `uuuu-MM-dd HH:mm:ss` 和注入的 `Clock` 校验“不得超过业务时间未来五分钟”。
  - 账户号格式合法后才查询账户；不存在或已软删除映射为 `ACCOUNT_NOT_FOUND`，存在但为 `DISABLED` 映射为 `ACCOUNT_NOT_ACTIVE`。
  - 只有本地字段全部合法且账户为 `ACTIVE` 的记录才能形成业务键 `(accountId, CSV_IMPORT, externalTransactionNo)`；无效记录不占用文件内“第一条有效记录”的位置。
  - 文件内重复按 CSV 逻辑记录号稳定判定：第一个有效业务键保留，后续相同键记为 `DUPLICATE_TRANSACTION_IN_FILE`；再检查第一个键是否已存在于数据库并映射为 `DUPLICATE_TRANSACTION`。
  - 数据库重复检查必须包含已软删除的 `CSV_IMPORT` 交易，同时不能把相同键的 `MANUAL` 交易误判为重复；流水号比较保持大小写敏感。
  - 最多 10,000 行的账户和交易查询采用去重、分批读取，禁止每行各执行一次查询形成 N+1；数据库唯一约束仍由 Day 5 写入阶段承担并发兜底。
  - 行错误结果携带逻辑记录号、固定字段名、`ImportRowErrorCode`、安全消息和最多 255 字符的拒绝值；不保存整行原文、SQL、路径、堆栈或第三方异常。
- **执行顺序**：
  1. 评审 Day 1 字段/错误契约和 Day 3 交接对象，产出 Day 4 设计文档与结果模型。
  2. 先实现不访问数据库的字段规范化和本地校验，并用固定 `Clock` 完成边界单元测试。
  3. 扩展账户与交易只读 Mapper，按去重后的键分批查询，并用真实 MySQL 验证软删除、来源和大小写语义。
  4. 组合批量账户解析、文件内重复和数据库重复判断，形成顺序稳定、不可变的文件校验结果。
  5. 运行聚焦测试、完整回归、真实 SQL/副作用检查、健康检查和范围审计。
- **任务**：
  - [x] 新增 Day 4 设计文档，锁定组件职责、错误优先级、结果对象、批量查询策略以及 Day 5 交接边界。
  - [x] 定义不可变的规范化交易候选、行错误和值校验结果；合法候选固定携带 `TransactionSource.CSV_IMPORT`。
  - [x] 实现六列数量校验，以及账户号、流水号、方向、金额、交易时间和描述的规范化与本地校验。
  - [x] 复用现有账户/交易业务规则；人工 CRUD 的现有行为和测试保持不变，没有引入相互矛盾的第二套业务语义。
  - [x] 实现账户批量解析，区分 `ACCOUNT_NOT_FOUND` 与 `ACCOUNT_NOT_ACTIVE`。
  - [x] 实现文件内重复判断，保证只有第一个本地合法且账户有效的业务键可以继续。
  - [x] 实现数据库已有 CSV 业务键的分批只读查询，包含软删除交易并保持流水号大小写敏感。
  - [x] 按稳定字段顺序生成安全的行错误；拒绝值超过存储上限时截断并明确标记，同一记录多个错误不重复计算失败行。
  - [x] 单元测试覆盖全部字段的合法值、边界值、非法值、规范化结果、固定时间边界、多个错误和列数错误。
  - [x] MySQL 集成测试覆盖 ACTIVE/DISABLED/不存在/软删除账户，文件内重复，已有/软删除 CSV 重复，MANUAL 非重复和流水号大小写差异。
  - [x] 验证批量查询在大批量输入下不会退化为逐行 N+1，结果顺序与 CSV 逻辑记录号一致且不可修改。
- **关键文件**：
  - `docs/design/week3-day4-import-row-validation-design.md`
  - `src/main/java/com/finguard/core/importjob/validation/`
  - `src/main/java/com/finguard/core/account/mapper/AccountMapper.java`
  - `src/main/java/com/finguard/core/transaction/mapper/TransactionMapper.java`
  - `src/test/java/com/finguard/core/importjob/validation/`
- **验收**：
  - [x] 合法样例得到规范化账户 ID、流水号、方向、两位小数金额、毫秒为零的交易时间、可空描述和固定 `CSV_IMPORT` 来源。
  - [x] Day 1 定义的 11 种 `ImportRowErrorCode` 均有自动化测试，错误字段、逻辑记录号、拒绝值和安全消息稳定。
  - [x] 已删除账户按不存在处理；已删除 CSV 交易仍判重复；MANUAL 同业务键不误判；`EXT-001` 与 `ext-001` 保持不同。
  - [x] 较早的无效记录不抢占文件内重复键；同一记录即使产生多个错误也只进入失败记录集合一次。
  - [x] 账户和交易查重为去重后的 500 条分批查询，不存在按 CSV 行数增长的 N+1 查询。
  - [x] 校验过程对 `accounts`、`transactions`、`import_jobs` 和 `import_row_errors` 均无写入副作用。
  - [x] MySQL 为 `healthy`；Day 4 聚焦测试 21/21、`mvn clean test` 199/199 通过且无跳过。
  - [x] 应用健康检查为 `UP`，端口 8080 已释放，测试数据与临时文件已清理，`git diff --check` 通过。
  - [x] 变更中没有 Controller、任务状态流转、交易/行错误入库、V5、RabbitMQ、Redis 或自动对账实现。
- **学习重点**：
  - 现在掌握：纯校验与数据库查询分层、`BigDecimal` 精度、严格时间解析、`Clock` 可测试性、不可变结果、错误聚合、批量查询与 N+1。
  - Day 5 再学：multipart 上传、任务状态机、事务边界、批量插入、唯一键并发兜底和失败恢复。
  - 暂不展开：RabbitMQ 可靠消息、Redis、自动对账和审核流程。
- **回滚**：若 Day 4 需要回滚，只删除新增校验组件和只读查询方法；不涉及数据库迁移或已有数据恢复，Day 3 的解析结果仍可独立使用。
- **验收结论**：真实 MySQL `EXPLAIN` 分别使用 `uk_accounts_account_no` 和 `uk_transactions_account_source_external_no`；软删除、来源、大小写、500+1 分批查询和零写入副作用均由自动化测试覆盖。
- **提交**：`feat: validate CSV import rows`

### Week 3 Day 5：同步 CSV 上传与持久化闭环

- **状态**：已完成
- **业务目标**：把 Day 2～Day 4 已完成的任务持久层、文件解析和逐行校验串成一个可通过真实 HTTP 调用的同步导入闭环；同一原始文件只处理一次，合法行写入交易，错误行可追踪，任务状态和统计始终与数据库结果一致。
- **请求流**：

  ```text
  ADMIN 以 multipart/form-data 上传 file
    → 在创建任务前完成请求级检查并读取原始字节
    → 对原始字节计算 SHA-256
    → 命中已有 fileHash：返回原任务（200，duplicateFile=true）
    → 未命中：创建 PENDING 任务（createdBy 取 JWT subject）
    → 独立事务推进为 PROCESSING
    → 解析 CSV 文件结构并执行 Day 4 逐行校验
    → 受控事务写入 CSV_IMPORT 交易和行错误
    → 汇总统计并进入 SUCCESS / PARTIAL_SUCCESS / FAILED
    → 返回新任务（201，Location=/api/import-jobs/{id}）
  ```

- **范围边界**：
  - 本日实现 `POST /api/import-jobs`、`GET /api/import-jobs/{id}` 和 `GET /api/import-jobs/{id}/errors`；上传仅 ADMIN，两个查询接口允许 ADMIN 和 REVIEWER。
  - 本日只完成同步导入；不引入 RabbitMQ、重试、死信、消费者幂等或异步轮询语义。
  - 本日新增最小 V5，把 `CSV_IMPORT` 交易关联到来源导入任务；不创建对账、风险、审核、审计或 MQ 表。
  - 本日不执行自动对账，不修改已有 MANUAL 交易 CRUD，不提供重复文件强制重跑、失败任务重试、覆盖导入或任务删除接口。
  - 文件仍只在内存中处理，不落本地磁盘，不保存整条原始 CSV，不把 SQL、路径、堆栈或第三方异常暴露给客户端。

- **关键设计决定**：
  - 把 Day 3 解析入口最小拆分为“请求检查与指纹准备”和“文件结构解析”两个阶段：请求级失败不创建任务；哈希确定后先做文件幂等并创建任务；UTF-8、表头、CSV 结构和行数等文件级失败发生在任务创建后。
  - `transactions` 新增可空 `import_job_id` 和 `RESTRICT` 外键；`MANUAL` 必须为 `null`，`CSV_IMPORT` 必须关联一个任务。新增 V5，禁止修改 V1～V4，并同步更新迁移版本、表结构和测试夹具断言。
  - 文件幂等采用“应用层按哈希预查 + `uk_import_jobs_file_hash` 并发兜底”：并发插入冲突后必须在新事务中读取并返回原任务，不能把重复文件当成通用 `409`，也不能重新解析或重复写交易。
  - 状态推进采用编排 Service 与事务执行 Service 分离，避免同类方法调用使 `@Transactional` 失效：任务创建、进入 `PROCESSING`、业务写入和系统失败恢复使用明确的事务边界。
  - 文件级失败在已有任务上保存稳定 `ImportFileErrorCode` 和安全摘要，终态为 `FAILED`，不写交易和普通行错误。
  - 行级错误是正常业务结果，不抛异常回滚其他合法行；合法交易、行错误、四项统计和最终状态在同一个处理事务中提交。
  - 交易和行错误按固定批次写入。交易批次若发生唯一键竞态，先回滚该批次的保存点，再逐行重放定位冲突；只有命中 `uk_transactions_account_source_external_no` 才转成 `DUPLICATE_TRANSACTION`，其他完整性错误继续按系统失败处理。禁止使用会吞掉其他数据错误的 `INSERT IGNORE`。
  - 最终统计必须满足 `totalRows = successRows + failedRows`；同一逻辑记录有多个错误时 `failedRows` 只计一次，`duplicateRows` 只统计含两类重复错误的失败记录。全部成功为 `SUCCESS`，有成功也有失败为 `PARTIAL_SUCCESS`，零成功为 `FAILED`。
  - 未预期异常必须使本次处理事务中的交易、行错误和终态更新整体回滚，再由独立事务把仍处于 `PROCESSING` 的任务标记为 `FAILED/PROCESSING_FAILED`；不能出现任务显示成功但交易只落库一部分。
  - 上传人只从已验签 JWT 的数字 `subject` 提取，不接受请求参数伪造 `createdBy`；统一异常处理负责缺少文件、非法文件、超限 `413`、任务不存在 `404` 和安全的 `500`。

- **执行顺序**：
  1. 先产出 Day 5 设计文档，锁定接口 DTO/VO、状态机、事务传播、批处理降级、异常映射和并发测试方案。
  2. 调整文件准备/解析边界并配置 multipart 大小限制，保持 Day 3 的哈希、UTF-8、BOM、换行和 CSV 语义不变。
  3. 新增 V5、交易任务关联和 Mapper 写入能力，先用真实 MySQL 验证外键、来源约束和批量 SQL。
  4. 实现同步导入编排、文件哈希幂等、任务状态推进、交易/行错误写入、统计汇总和独立失败恢复。
  5. 实现上传、任务详情、错误分页 DTO/VO、Controller、统一错误映射和精确 RBAC 路由。
  6. 先跑纯单元/Controller 测试，再跑真实 MySQL 集成、并发幂等和故障注入测试。
  7. 执行完整回归、真实 JWT + HTTP + MySQL 验收、数据清理、端口清理、范围审计和 `git diff --check`。

- **任务**：
  - [x] 新增 `docs/design/week3-day5-sync-import-orchestration-design.md`，画出首次上传、重复上传、文件失败、行错误和系统失败五条时序。
  - [x] 建立文件准备结果，使请求级检查、原始字节 SHA-256、文件哈希预查和结构解析的先后顺序符合 Day 1 契约。
  - [x] 配置 multipart 上传上限，并把缺少文件、空文件、非法扩展名和超过 5 MiB 分别映射为统一 `400/413`；被拒绝请求不得创建任务。
  - [x] 新增 `V5__link_import_jobs_to_transactions.sql`，为交易建立导入任务外键和来源关联约束；更新 `Transaction`、数据库基线测试及所有受影响夹具。
  - [x] 扩展 Mapper：条件式任务状态更新、交易/行错误分批写入、任务详情和错误稳定分页；不添加没有查询路径依据的索引。
  - [x] 实现首次任务创建和哈希幂等；覆盖串行重复与并发重复，保证只存在一个任务 ID，交易不会重复增加。
  - [x] 实现 `PENDING → PROCESSING → SUCCESS/PARTIAL_SUCCESS/FAILED` 的正向状态校验，以及 `startedAt`、`finishedAt`、统计和安全摘要填写。
  - [x] 将 `ValidatedImportRow` 转为固定 `CSV_IMPORT` 且携带 `importJobId` 的交易，将 `ImportRowValidationError` 转为 `ImportRowError`。
  - [x] 实现批次写入和唯一键冲突降级，把并发产生的交易业务键冲突追加为稳定行错误，同时保留其他合法行。
  - [x] 实现文件级失败与未预期系统失败的两种恢复路径，证明后者会回滚本次交易和行错误后再独立标记任务失败。
  - [x] 实现上传响应、任务详情和 `PageResponse` 错误分页；首次任务返回 `201 + Location`，重复文件返回 `200 + duplicateFile=true`。
  - [x] 更新 Security 路由：ADMIN 可上传，ADMIN/REVIEWER 可查询，匿名为 `401`，REVIEWER 上传为 `403`，拒绝请求无数据库副作用。
  - [x] 单元与 Controller 测试覆盖状态选择、统计去重、DTO 映射、JWT subject、HTTP 状态、Location、错误分页和安全错误响应。
  - [x] MySQL 集成测试覆盖全成功、部分成功、全行失败、五类文件失败、跨文件重复、软删除重复、MANUAL 同键共存、串行/并发同文件、唯一键竞态和系统故障回滚。

- **关键文件**：
  - `docs/design/week3-day5-sync-import-orchestration-design.md`
  - `src/main/resources/db/migration/V5__link_import_jobs_to_transactions.sql`
  - `src/main/resources/application.yml`
  - `src/main/java/com/finguard/core/importjob/controller/`
  - `src/main/java/com/finguard/core/importjob/dto/`
  - `src/main/java/com/finguard/core/importjob/service/`
  - `src/main/java/com/finguard/core/importjob/parser/`
  - `src/main/java/com/finguard/core/importjob/mapper/`
  - `src/main/java/com/finguard/core/transaction/entity/Transaction.java`
  - `src/main/java/com/finguard/core/transaction/mapper/TransactionMapper.java`
  - `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`
  - `src/main/java/com/finguard/core/common/exception/`
  - `src/test/java/com/finguard/core/importjob/`

- **验收**：
  - [x] `docker compose ps` 显示 MySQL 为 `healthy`；空库可从 V1 迁移到 V5，V1～V4 校验和不变。
  - [x] 合法文件通过真实 MySQL 集成测试后任务为 `SUCCESS`，交易均为 `CSV_IMPORT` 且关联正确任务；真实 ADMIN JWT 部分成功上传返回 `201 + Location`。
  - [x] 同一文件再次或并发上传返回同一任务，只有首次请求创建数据；不同字节但相同业务键按行级重复处理。
  - [x] 部分成功、全部行失败和文件级失败的状态、统计、错误码、行错误与数据库副作用完全符合 Day 1 契约。
  - [x] 故障注入证明未预期异常不会留下本次交易或行错误，任务最终为 `FAILED/PROCESSING_FAILED`。
  - [x] ADMIN/REVIEWER/匿名权限矩阵通过；任务详情和错误分页顺序稳定，查询不存在任务为统一 `404`。
  - [x] Day 5 新增同步导入测试 12/12、解析回归 16/16、完整 `mvn clean test` 214/214，均无跳过。
  - [x] 真实 HTTP/MySQL 验收得到 `PARTIAL_SUCCESS|3|2|1|0`、2 条关联交易和 1 条行错误；重复上传、权限矩阵、数据清理和端口 8080 清理通过。
  - [x] `git diff --check` 通过；范围中没有 RabbitMQ、Redis、自动对账、风险、审核、审计、重跑或覆盖导入实现。

- **学习重点**：
  - 必须掌握：multipart 请求、SHA-256 文件幂等、数据库唯一约束、状态机、Spring 事务传播/代理、保存点、批处理、异常分层和 JWT 当前用户。
  - 边做边学：并发唯一键冲突、故障恢复、稳定分页、HTTP `201/200/400/413/404` 语义和真实集成测试。
  - 暂不展开：RabbitMQ 可靠消息、分布式事务、Redis 幂等、自动对账、风险策略和审核状态机。

- **回滚**：代码和路由可按 Day 5 文件范围回退；V5 一旦在共享数据库执行不得修改或删除，若必须撤销只能新增补偿迁移。回滚后 Day 2～Day 4 的持久层、解析器和校验器仍可独立使用。
- **验收结论**：首次真实上传返回 `201/PARTIAL_SUCCESS`，相同原始字节再次上传返回同一任务的 `200`；REVIEWER 查询为 `200`、上传为 `403`，匿名查询为 `401`。数据库状态、统计和关联记录一致，验收用户、账户、任务、交易、错误、临时文件和监听端口均已清理。
- **提交**：`feat: implement synchronous CSV import`

### Week 3 Day 6：同步版自动对账

- **状态**：已完成
- **业务目标**：以一个已经完成的 CSV 导入任务为边界，把其中成功入库的外部 `CSV_IMPORT` 交易与系统中的内部 `MANUAL` 交易进行可解释、可重复执行的自动对账，持久化 `MATCHED / UNMATCHED / DUPLICATE / SUSPICIOUS` 结果，为 Week 5 的人工审核与审计留下稳定输入。
- **请求流**：

  ```text
  ADMIN 提交 importJobId
    → 校验导入任务存在且状态为 SUCCESS / PARTIAL_SUCCESS
    → 按 importJobId 幂等查找或创建 PENDING 对账任务
    → 条件式推进为 PROCESSING
    → 读取该任务下未删除的 CSV_IMPORT 交易
    → 分批读取同账户、未删除的 MANUAL 候选交易
    → 先保留外部流水号一致的候选，再执行金额/方向/时间窗口规则
    → 稳定判定 MATCHED / UNMATCHED / DUPLICATE / SUSPICIOUS
    → 同一事务写入逐笔结果、汇总统计并完成任务
    → 首次返回 201 + Location；重复触发返回原任务 200
  ```

- **第一版对账规则**：
  - 只在同一账户内比较 `CSV_IMPORT` 与 `MANUAL`；两边已软删除的交易均不参与，描述字段不参与匹配。
  - 金额使用 `BigDecimal.compareTo` 精确比较，方向必须一致，外部流水号继续按大小写敏感语义比较。
  - 时间容差固定为前后 3 天且包含边界；第一版不做金额误差、汇率、多时区、文本相似度或机器学习模糊匹配。
  - 同账户、外部流水号、方向、金额和时间完全一致，结果为 `MATCHED`，匹配方式为 `EXACT`。
  - 外部流水号一致，且方向、金额一致、时间处于容差内，结果为 `MATCHED`，匹配方式为 `TOLERANCE`。
  - 外部流水号一致但方向、金额或时间窗口不满足，结果为 `SUSPICIOUS`，保存稳定原因码，不再用弱规则改配其他交易。
  - 没有同流水号候选时，再按“同账户 + 同方向 + 同金额 + 时间窗口”寻找候选：唯一候选为 `MATCHED/TOLERANCE`，没有候选为 `UNMATCHED`，多个候选为 `DUPLICATE`。
  - 为保证一对一匹配，先处理并保留外部流水号一致的候选，再处理弱匹配；同一 `MANUAL` 被多个外部交易竞争时，冲突项为 `DUPLICATE`。输入和候选均使用稳定 ID 顺序，重复运行不得因遍历顺序变化结果。

- **范围边界**：
  - 本日实现 `POST /api/reconciliation-jobs`、`GET /api/reconciliation-jobs/{id}` 和 `GET /api/reconciliation-jobs/{id}/results`；触发仅 ADMIN，查询允许 ADMIN 和 REVIEWER。
  - 只接受状态为 `SUCCESS` 或 `PARTIAL_SUCCESS` 且至少有一条成功交易的导入任务；文件失败、全部行失败、仍在处理或不存在的任务不能开始对账。
  - 一个导入任务只对应一个对账任务；重复或并发触发返回原任务，不重复写结果。对账任务状态只允许 `PENDING → PROCESSING → COMPLETED/FAILED`。
  - 本日使用同步执行，不在 CSV 上传事务内自动启动，不引入 RabbitMQ、Publisher Confirm、ACK、重试、死信或消费者幂等；这些留给 Week 4。
  - 本日不修改、删除或补写原始 `transactions`，不实现风险策略、人工确认/忽略、审核状态机、乐观锁、审计日志、Redis 或统计缓存。

- **关键设计决定**：
  - 新增 V6，而不是修改已经应用的 V1～V5。V6 建立 `reconciliation_jobs` 和 `reconciliation_results`，并通过外键保留导入任务、外部交易、候选内部交易和创建人的追踪关系。
  - `reconciliation_jobs` 至少保存导入任务 ID、状态、总数、四类结果计数、创建人、失败摘要和开始/结束时间；`import_job_id` 唯一约束作为并发幂等最终兜底。
  - `reconciliation_results` 一条外部交易只保存一个最终结果，至少包含对账任务 ID、外部交易 ID、可空内部交易 ID、结果类型、匹配方式、稳定原因码和创建时间；唯一键保证同一任务不会重复产生同一外部交易结果。
  - 汇总必须满足 `totalCount = matchedCount + unmatchedCount + duplicateCount + suspiciousCount`，且与当前任务的结果行数一致；业务分类不是系统异常，任务仍进入 `COMPLETED`。
  - 任务创建、进入 `PROCESSING`、结果写入与完成、系统失败恢复使用清晰的 Spring 事务边界。未预期异常必须回滚本次全部结果和统计，再由独立事务把仍在 `PROCESSING` 的任务标记为 `FAILED`。
  - 候选读取必须按账户和时间范围分批完成，禁止每条 CSV 交易单独查询数据库形成 N+1；在新增索引前先用真实 SQL 与 `EXPLAIN` 验证现有唯一键和 `idx_transactions_account_time` 是否足够。
  - 结果分页固定按 `csv_transaction_id ASC, id ASC`，可选 `resultType` 精确筛选；默认 `page=1,size=20`，最大 `size=100`。

- **执行顺序**：
  1. 新增 Day 6 设计文档，锁定四类结果、匹配优先级、一对一冲突、原因码、状态机、接口和事务时序。
  2. 设计 V6 表、约束和查询路径，先用真实 MySQL SQL/`EXPLAIN` 评审是否需要索引，再写迁移和数据库基线断言。
  3. 建立 `reconciliation` 模块的枚举、实体、Mapper、DTO/VO 和纯规则判定组件，先用单元测试覆盖全部分类与边界。
  4. 实现候选批量加载、两阶段稳定匹配、任务幂等、结果批量写入、汇总统计和系统失败恢复。
  5. 实现触发、任务详情、结果筛选分页、统一错误映射和精确 RBAC 路由。
  6. 运行聚焦单元/Controller 测试、真实 MySQL 集成测试、并发幂等与故障注入测试。
  7. 执行完整回归、真实 JWT + HTTP + MySQL 验收、数据清理、端口清理、范围审计和 `git diff --check`。

- **任务**：
  - [x] 新增 `docs/design/week3-day6-sync-reconciliation-design.md`，给出规则决策表、两阶段匹配伪代码、状态图、接口模型、事务时序和失败矩阵。
  - [x] 新增 `V6__create_reconciliation_tables.sql`；保持 V1～V5 不变，并同步更新最新 Flyway 版本、业务表数量、约束和清理顺序断言。
  - [x] 建立 `reconciliation` 模块的任务/结果状态、匹配方式、原因码、实体和 Mapper。
  - [x] 实现导入任务前置条件校验、按 `importJobId` 的应用层预查和数据库唯一键并发兜底。
  - [x] 实现批量候选查询和稳定两阶段匹配，覆盖精确匹配、容差匹配、无候选、同流水号字段冲突、多候选和同一内部交易竞争。
  - [x] 实现结果分批写入、四类统计、`COMPLETED` 提交和未预期异常的整体回滚/独立 `FAILED` 恢复。
  - [x] 实现触发响应、任务详情和按结果类型筛选的稳定分页；首次触发返回 `201 + Location`，重复触发返回 `200`。
  - [x] 更新 Security 路由和统一错误处理：ADMIN 可触发，ADMIN/REVIEWER 可查询，非法导入状态为 `409`，不存在为 `404`，匿名为 `401`，REVIEWER 触发为 `403`。
  - [x] 单元测试覆盖 3 天边界、金额精确比较、流水号大小写、软删除排除、规则优先级、一对一分配、稳定顺序和统计守恒。
  - [x] MySQL 集成测试覆盖 V6 约束、空结果拒绝、四类结果共存、批量查询无 N+1、串行/并发重复触发、结果分页和系统故障回滚。
  - [x] 使用真实 ADMIN/REVIEWER JWT 完成同步触发、详情、筛选分页和权限验收，并用 SQL 核对任务、结果、关联交易与统计。

- **关键文件**：
  - `docs/design/week3-day6-sync-reconciliation-design.md`
  - `src/main/resources/db/migration/V6__create_reconciliation_tables.sql`
  - `src/main/java/com/finguard/core/reconciliation/model/`
  - `src/main/java/com/finguard/core/reconciliation/entity/`
  - `src/main/java/com/finguard/core/reconciliation/mapper/`
  - `src/main/java/com/finguard/core/reconciliation/service/`
  - `src/main/java/com/finguard/core/reconciliation/controller/`
  - `src/main/java/com/finguard/core/reconciliation/dto/`
  - `src/main/java/com/finguard/core/reconciliation/vo/`
  - `src/main/java/com/finguard/core/transaction/mapper/TransactionMapper.java`
  - `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`
  - `src/main/java/com/finguard/core/common/exception/`
  - `src/test/java/com/finguard/core/reconciliation/`
  - `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`

- **验收**：
  - [x] MySQL 为 `healthy`；数据库已从 V5 迁移到 V6，V1～V5 校验和不变，真实约束与 `EXPLAIN` 结果已记录。
  - [x] 同一组数据稳定产生 `MATCHED / UNMATCHED / DUPLICATE / SUSPICIOUS`，每条外部交易只有一个结果，统计守恒且关联可追踪。
  - [x] 3 天边界、精确/容差优先级、同流水号冲突、多候选和内部交易竞争均符合规则决策表。
  - [x] 同一导入任务串行或并发触发只产生一个对账任务和一组结果；重复请求不重新执行或增加结果。
  - [x] 故障注入证明结果和完成统计整体回滚，任务最终安全进入 `FAILED`，不存在半套结果或错误完成状态。
  - [x] ADMIN/REVIEWER/匿名权限矩阵、详情和结果筛选分页通过；拒绝请求无数据库副作用。
  - [x] Day 6 新增对账测试 15/15、完整 `mvn clean test` 230/230，均无失败、错误或跳过。
  - [x] 真实 ADMIN/REVIEWER JWT + HTTP + MySQL 得到 `COMPLETED|5|2|1|1|1|RESULTS:5`；首次/重复触发、权限矩阵、验收数据和监听端口清理通过。
  - [x] `git diff --check` 通过；范围中没有 MQ、Redis、风险、审核或审计实现。

- **学习重点**：
  - 必须掌握：可解释规则建模、`BigDecimal` 比较、时间窗口、稳定排序、一对一匹配、数据库约束、批量查询、幂等和事务失败恢复。
  - 边做边学：两阶段匹配、候选冲突、规则决策表、N+1 检查、`EXPLAIN`、结果分页和并发唯一键兜底。
  - 暂不展开：复杂模糊匹配、机器学习、RabbitMQ 可靠消息、Redis、风险规则、人工审核和审计追踪。

- **回滚**：代码和路由可按 Day 6 文件范围回退；V6 一旦在共享数据库执行不得修改或删除，若必须撤销只能新增补偿迁移。回滚不得修改 Day 5 已持久化的导入任务和交易。
- **验收结论**：真实同步触发返回 `201/COMPLETED`，相同导入任务再次触发返回同一任务的 `200`；四类统计为 `2/1/1/1` 且 5 条结果关联正确。REVIEWER 查询为 `200`、触发为 `403`，MySQL 统计一致，验收用户、账户、交易、导入任务、对账任务、结果和 8080 端口均已清理。
- **提交**：`feat: implement synchronous reconciliation`

### Week 3 Day 7：综合验收与周复盘

- **状态**：已完成
- **目标**：在干净的真实 MySQL 环境中验证 Week 3 从 CSV 上传到自动对账查询的完整闭环，确认权限、幂等、失败恢复、数据一致性和范围边界，并形成可复核的周复盘。
- **范围边界**：只做 Week 3 已实现能力的综合验收、文档复盘、测试与环境清理；不新增 RabbitMQ、Redis、风险规则、人工审核、审计日志或前端。
- **执行顺序**：
  1. 补充本日验收清单与周复盘文档，核对 Day 1 契约和 Day 2–6 实际交付。
  2. 执行导入、对账、认证和持久层聚焦测试，随后执行完整 `mvn clean test`。
  3. 重建项目专属 MySQL 数据卷，确认 V1→V6 迁移、业务表、约束和索引状态。
  4. 使用真实 ADMIN/REVIEWER JWT 完成上传、任务详情、错误分页、对账触发、结果筛选、重复请求和权限矩阵验收。
  5. 注入一次导入/对账失败恢复场景，使用 SQL 核对任务状态、交易数、结果数和统计守恒。
  6. 清理验收数据、停止应用、释放 8080 端口，执行范围审计、`git diff --check` 和最终 Git 提交。
- **任务**：
  - [x] 新增 `docs/review/week3-review.md`，记录功能闭环、测试结果、真实 HTTP/MySQL 证据、失败恢复、限制和 Week 4 边界。
  - [x] 更新本节状态、任务、验收结论和 Git 里程碑索引，所有勾选项均有命令或测试证据。
  - [x] 聚焦测试 34/34 和完整 `mvn clean test` 230/230 通过，零失败、零错误、零跳过。
  - [x] 干净数据卷完成 V1→V6 迁移，真实数据库约束、外键、唯一键、索引和统计守恒通过。
  - [x] 真实 ADMIN/REVIEWER JWT + HTTP 覆盖 CSV 导入成功、重复文件、任务/错误查询、对账结果、重复触发和权限矩阵。
  - [x] 导入与对账失败恢复后不存在半套交易/结果或错误完成状态，验收数据全部清理，8080 端口释放。
  - [x] 范围审计确认没有提前实现 Week 4–6 能力；`git diff --check` 通过。
- **关键文件**：
  - `TASKS.md`
  - `docs/review/week3-review.md`
  - `src/test/java/com/finguard/core/importjob/`
  - `src/test/java/com/finguard/core/reconciliation/`
  - `src/test/java/com/finguard/core/auth/`
  - `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`
- **验收**：聚焦测试 34/34、完整 `mvn clean test` 230/230；真实 HTTP 覆盖 `201/200/403/404` 矩阵；干净卷 V1→V6、统计守恒、失败恢复、数据清理和端口释放均通过，详见 `docs/review/week3-review.md`。
- **提交**：`docs: complete week 3 acceptance review`

### Week 4 Day 1：异步导入与对账消息契约设计

- **状态**：已完成
- **业务目标**：在 Week 3 同步导入和同步对账闭环之上，设计可持久化、可异步执行、可重试和可恢复的 RabbitMQ 消息流程；HTTP 请求只负责受理任务，消费者负责业务处理，不能因为重复投递而重复写入交易或对账结果。
- **当前基线**：Week 3 已完成 `POST /api/import-jobs` 的同步文件处理、SHA-256 文件幂等、任务状态流转、批量交易入库、失败恢复，以及独立的同步对账触发、四类结果和并发幂等；完整测试和真实 MySQL/JWT/HTTP 综合验收已完成。
- **请求流设计**：
  ```text
  导入：ADMIN 上传 file
    → 请求级检查、读取原始字节、计算 SHA-256
    → 命中已有 fileHash：返回原任务，不创建新任务、不重复入队
    → 未命中：一个数据库事务内创建 PENDING、保存原始文件、写入 IMPORT_REQUESTED Outbox
    → 返回 202 + Location
    → Outbox Relay 发布消息并等待 Publisher Confirm 与 mandatory return
    → 导入消费者条件式推进 PENDING → PROCESSING
    → 复用 Week 3 解析/校验/入库逻辑
    → 事务提交后手动 ACK

  对账：ADMIN 提交 importJobId
    → 创建或复用 PENDING 对账任务并写入 Outbox
    → 返回 202 + Location
    → Relay 发布 RECONCILIATION_REQUESTED
    → 对账消费者复用 Week 3 匹配、结果写入和统计守恒逻辑
    → 事务提交后手动 ACK
  ```
- **范围边界**：
  - 本日只完成异步架构、HTTP 契约、消息契约、可靠性边界、文件持久化方案、测试矩阵和后续执行顺序设计。
  - 不添加 RabbitMQ 依赖、`@RabbitListener`、Docker RabbitMQ 服务、V7/V8 迁移或运行时代码。
  - 不修改已应用的 V1～V6，不改变 Week 3 的 CSV 字段、SHA-256、行校验、交易唯一键和对账匹配规则。
  - 不引入 Redis、风险规则、人工审核、乐观锁、审计、监控、前端或 MinIO。
  - 不提供 exactly-once、强制重跑、覆盖导入或无限 `requeue=true`。
- **执行顺序**：
  1. 复盘现有 `importjob` 和 `reconciliation` 同步调用链，标记 HTTP 编排、事务业务和未来消费者边界。
  2. 锁定首次受理 `202 + Location`、重复请求复用原任务、任务查询轮询和权限矩阵。
  3. 解决当前原始 CSV 只在 HTTP 内存中的问题，确定独立 `import_job_files` 表保存原始字节的方案。
  4. 定义导入/对账消息字段、`schemaVersion`、稳定 `messageId`、exchange、queue、routing key、重试队列和 DLQ。
  5. 定义任务创建事务、Outbox 发布事务、Confirm + mandatory return、消费者业务事务和手动 ACK 的边界。
  6. 区分 Outbox 发布重试与消费者业务重试，并定义业务错误、临时系统错误、重复消息、ACK 失败和超过次数后的死信处理。
  7. 登记 Day 2～Day 7 的实现顺序、测试矩阵、回滚边界和面试解释要点。
- **任务**：
  - [x] 新增 `docs/design/week4-day1-async-messaging-contract.md`。
  - [x] 定义导入和对账的异步请求流，保留 ADMIN/REVIEWER 权限边界和独立手动对账触发。
  - [x] 定义 `202 + Location` 受理语义、重复文件/重复触发语义和任务状态查询语义。
  - [x] 确定原始 CSV 独立持久化方案，明确后续新增迁移、禁止修改 V1～V6。
  - [x] 定义只携带任务 ID 的消息格式、事件类型、版本、稳定消息 ID和持久化投递要求。
  - [x] 定义持久化 exchange、主队列、两级重试队列、DLQ 和禁止无限 requeue 的边界。
  - [x] 区分 Publisher Confirm、mandatory return、消费者手动 ACK、Outbox、业务幂等和数据库唯一键的职责。
  - [x] 定义 Outbox 退避、重复消费、并发消费、数据库提交后 ACK 失败、临时异常、非法消息和业务异常的处理矩阵。
  - [x] 登记 Day 2～Day 7 任务顺序、学习重点、验收标准和回滚方案。
- **关键设计决定**：
  - 首次异步导入和对账请求返回 `202 Accepted`，任务详情通过现有 GET 接口轮询；重复文件和重复对账请求仍复用原任务，不重新创建业务任务。
  - 原始 CSV 不放进 RabbitMQ 消息；新增独立 `import_job_files` 表保存原始字节，后续通过新的 Flyway 迁移实现。
  - 任务、原始文件和 Outbox 事件在同一 MySQL 事务内提交；Outbox Relay 发布持久化消息，只有 Confirm ACK 且没有 mandatory return 才标记 `SENT`。
  - Outbox 发布失败按 `next_attempt_at` 有限批量退避，且重试保持稳定 `messageId`；它与消费者 retry queue/DLQ 是两条独立链路。
  - 采用至少一次投递，允许消息重复；消费者通过任务状态条件更新、处理租约、业务幂等和数据库唯一键避免重复交易/结果及卡死任务。
  - 业务错误正常落库并 ACK；临时系统错误进入有限重试；超过最大次数后进入 DLQ，并用独立事务把仍处理中的任务标记为安全失败。
  - Week 4 不把上传和对账自动绑定，保留 Week 3 的独立手动对账入口。
- **关键文件**：
  - `docs/design/week4-day1-async-messaging-contract.md`
  - `TASKS.md`
  - 后续实现预计涉及 `pom.xml`、`docker-compose.yml`、`application.yml`、`importjob`、`reconciliation` 和新的 Flyway 迁移；本日不修改这些代码文件。
- **验收**：
  - [x] 设计文档覆盖当前同步基线、异步导入/对账流程、HTTP 契约、状态机和权限边界。
  - [x] 设计文档覆盖原始文件持久化、消息字段、拓扑、Outbox、Publisher Confirm + mandatory return、手动 ACK、幂等、重试和 DLQ。
  - [x] 设计文档覆盖发布退避、业务错误/系统错误/非法消息/重复消息/ACK 失败/死信的决策表，以及 Day 2～Day 7 验收矩阵。
  - [x] 设计边界确认没有提前实现 RabbitMQ、Redis、风险、审核、审计、监控或数据库迁移。
  - [x] `git diff --check` 通过；完整 `mvn clean test` 保持 Week 3 基线通过，详见后续验收记录。
- **学习重点**：HTTP `202` 与异步任务、Exchange/Queue/Routing Key/Binding、Publisher Confirm、mandatory return 与 Consumer ACK、至少一次投递、Outbox、消费者幂等、事务边界、有限重试和死信队列。
- **回滚**：本日只有设计文档和任务清单变更；若设计评审否决异步契约，可删除新增设计文档并回退本节，不涉及 Java 代码、数据库数据、Flyway checksum 或 RabbitMQ 环境。后续数据库变更只能新增迁移，不能修改 V1～V6。
- **验收结论**：Day 1 设计文档和任务清单已完成，异步请求语义、原始文件持久化、消息契约、可靠投递和失败处理边界已锁定；实现从 Day 2 开始。
- **提交**：`docs: design week4 day1 async messaging contract`

### Week 4 Day 2：RabbitMQ 基础设施与基础拓扑

- **状态**：已完成
- **业务目标**：让 FinGuard Core 连接真实 RabbitMQ，并自动声明后续异步导入与对账所需的持久化基础拓扑；本日不改变 Week 3 的同步业务闭环。
- **范围边界**：
  - 只实现 AMQP 依赖、RabbitMQ Docker 服务、连接配置、基础 exchange/queue/binding 和连接/路由测试。
  - 不实现 `202 Accepted` 上传改造、文件持久化、Outbox、生产者、消费者、Publisher Confirm、手动 ACK、重试、DLQ 或业务消息处理。
  - 不新增 Flyway 迁移，不修改 V1～V6，不引入 Redis、风险、审核、审计、监控或前端。
- **执行顺序**：
  1. 核对 Day 1 锁定的命名、持久化和 Day 2～Day 6 边界。
  2. 增加 Spring AMQP 依赖、RabbitMQ Compose 服务和环境变量模板。
  3. 增加 Spring RabbitMQ 连接配置，凭据不写入源码。
  4. 声明导入和对账两个 direct durable exchange、两个 durable 主队列及其精确 routing binding。
  5. 用真实 RabbitMQ 测试队列存在和消息路由，再运行完整回归。
  6. 启动应用进行健康检查，清理测试消息和临时端口，完成范围审计。
- **关键文件**：
  - `docs/design/week4-day2-rabbitmq-foundation.md`
  - `pom.xml`
  - `docker-compose.yml`
  - `.env.example`
  - `src/main/resources/application.yml`
  - `src/main/java/com/finguard/core/messaging/config/RabbitMessagingConfiguration.java`
  - `src/test/java/com/finguard/core/messaging/RabbitMessagingTopologyIntegrationTest.java`
- **拓扑**：
  - `finguard.import.exchange` → `import.requested` → `finguard.import.queue`
  - `finguard.reconciliation.exchange` → `reconciliation.requested` → `finguard.reconciliation.queue`
  - 重试队列和死信队列留给 Day 6。
- **任务**：
  - [x] 增加 `spring-boot-starter-amqp`。
  - [x] 增加固定 RabbitMQ 4.3.4 Docker 服务、独立持久化 volume、仅本机暴露的管理端口和健康检查；保留旧 3.13 数据卷。
  - [x] 增加连接配置及 `.env.example` 环境变量模板。
  - [x] 声明导入/对账 exchange、主队列和 binding。
  - [x] 增加真实连接、持久化拓扑、正确/错误 routing key 路由和同步清理测试，并隔离未来 listener。
  - [x] 完成 RabbitMQ/MySQL/HTTP 验收、测试数据清理和端口清理。
- **验收**：
  - [x] RabbitMQ 4.3.4 和 MySQL 容器均为 `healthy`，RabbitMQ 仅绑定本机端口。
  - [x] 加固后的聚焦测试 `4/4` 通过。
  - [x] `mvn clean test` 为 `234/234`，无失败、错误或跳过。
  - [x] RabbitMQ 实际 exchange、queue、binding 检查及 Broker 重启持久化通过，测试消息和消费者清零。
  - [x] FinGuard 临时端口 `18080` 的 `/actuator/health` 返回 `HTTP 200 UP`，端口已释放。
  - [x] 8080 的无关 `cangqiong-server` 进程未被停止或修改。
  - [x] `git diff --check` 通过，变更范围未提前进入 Day 3～Day 6。
- **学习重点**：Exchange/Queue/Binding/Routing Key、direct exchange、durable topology、Spring AMQP 自动声明、Docker healthcheck 和环境变量配置。
- **回滚**：仅回退本日 AMQP 依赖、RabbitMQ 服务、连接/拓扑配置、聚焦测试和文档；不修改 V1～V6，不删除 RabbitMQ 数据卷。
- **验收结论**：RabbitMQ 基础设施和导入/对账基础拓扑已可运行、可连接、可路由，后续 Day 3 可在其上实现文件持久化和 Outbox。
- **提交**：`feat: add RabbitMQ messaging foundation`

### Week 4 Day 3：文件持久化、Outbox 与可靠发布

- **状态**：已完成
- **业务目标**：让导入/对账 HTTP 请求在一个 MySQL 事务内可靠保存 `PENDING` 任务及消息意图并返回 `202`，再由 Relay 通过 Publisher Confirm + mandatory return 可靠投递到 RabbitMQ。
- **范围边界**：
  - 实现 V7、`import_job_files`、`outbox_events`、异步受理、JSON 消息、持久化发布、确认/退回判定和发布退避。
  - 保留 Week 3 处理逻辑供后续消费者复用，但 HTTP 不再同步推进任务。
  - 不实现消费者、手动 ACK、消费重试/DLQ、Redis、风险、审核、审计、监控或前端。
- **执行顺序**：
  1. 完成 Day 3 设计与任务边界评审。
  2. 新增 V7 和持久层，验证任务、文件、Outbox 原子写入与数据库约束。
  3. 将导入/对账首次请求改为 `202 + Location + PENDING`，验证重复请求不重复建事件。
  4. 实现消息契约、Relay、correlated confirm、mandatory return 和发布失败退避。
  5. 完成聚焦测试、完整回归、真实 JWT/HTTP/MySQL/RabbitMQ/失败路径验收和清理。
- **任务**：
  - [x] 新增 `docs/design/week4-day3-outbox-publishing-design.md`，锁定事务、表、状态机、消息、失败和回滚边界。
  - [x] 新增 V7、文件/Outbox Entity、Mapper 与数据库约束测试。
  - [x] 原子创建导入任务、原始文件和导入事件；原子创建对账任务和对账事件。
  - [x] 首次导入/对账改为 `202/PENDING`，重复请求保持 `200` 且不重复入队。
  - [x] 实现持久化 JSON 消息、Relay、Confirm + mandatory return 和发布退避。
  - [x] 完成聚焦/全量测试、真实验收、清理、范围审计和提交前检查。
- **关键文件**：
  - `docs/design/week4-day3-outbox-publishing-design.md`
  - `src/main/resources/db/migration/V7__create_import_file_and_outbox_tables.sql`
  - `src/main/java/com/finguard/core/messaging/outbox/`
  - `src/main/java/com/finguard/core/importjob/`
  - `src/main/java/com/finguard/core/reconciliation/`
- **验收**：Day 3 聚焦测试 7/7、完整 `mvn clean test` 242/242 通过；真实 ADMIN/REVIEWER JWT + HTTP 覆盖导入/对账 `202/200/403/200`，MySQL 中任务保持 `PENDING`、文件字节完整、两类 Outbox 为 `SENT`，RabbitMQ 两个主队列均收到持久化消息；失败发布进入 `RETRY` 并退避；验收数据、消息和 18080 端口全部清理。
- **学习重点**：Transactional Outbox、至少一次发布、Publisher Confirm、mandatory return、稳定消息 ID、发布退避、HTTP 202 和数据库真源。
- **回滚**：Java/配置可回退；已应用 V7 不得修改或删除，只能新增迁移演进，数据卷不得作为回滚手段删除。
- **提交**：`feat: add reliable outbox publishing`

### Week 4 Day 4：异步导入消费者与手动 ACK

- **状态**：已完成
- **业务目标**：让 `finguard.import.queue` 中的 `IMPORT_REQUESTED` 消息真正驱动 CSV 导入；消费者从 MySQL 读取 Day 3 已持久化的原始文件，复用 Week 3 的解析、校验、交易/行错误入库和终态统计逻辑，并且只在业务事务成功提交后手动 ACK。
- **实现前基线**：Day 3 已把首次上传改为 `202/PENDING`，并原子保存任务、原始 CSV 和 Outbox；Relay 已能把持久化 JSON 消息可靠发布到导入主队列，但当时没有消费者，因此任务会停留在 `PENDING`。
- **请求流**：
  ```text
  Outbox Relay 发布 IMPORT_REQUESTED
    → finguard.import.queue
    → Listener 校验 messageId、eventType、aggregateId 和 schemaVersion
    → 以 aggregateId 定位 PENDING import_job
    → 从 import_job_files 读取原始 CSV 字节并恢复 PreparedImportFile
    → 同一业务事务内推进 PENDING → PROCESSING
    → 复用 Week 3 解析、逐行校验、批量交易/行错误入库和统计逻辑
    → 提交 SUCCESS / PARTIAL_SUCCESS / FAILED
    → 数据库事务成功提交
    → basicAck(deliveryTag, false)
  ```
- **范围边界**：
  - 本日只实现导入主队列消费者、消息基础校验、持久化文件读取、导入业务编排和手动 ACK；不实现对账消费者。
  - 正常成功、部分成功和可落库的文件级业务失败均形成确定终态，事务提交后 ACK；未预期系统异常必须回滚且不能 ACK，也不能把任务伪装成成功。
  - 本日只验证单次系统失败时“不提交错误业务结果、不 ACK、不静默丢消息”；重复投递、并发抢占、ACK 丢失、`PROCESSING` 租约和宕机恢复由 Day 5 完成。
  - 两级重试队列、重试次数、退避和 DLQ 由 Day 6 完成；Day 4 不使用无限 `requeue=true` 冒充可靠重试能力。
  - 不新增或修改 Flyway 迁移，不改变 V1～V7、CSV 字段契约、交易唯一键、HTTP 权限和 `202 + Location` 语义。
  - 不引入 Redis、风险规则、人工审核、审计、监控、前端或自动触发对账。
- **执行顺序**：
  1. 新增 Day 4 设计文档，锁定 Listener、业务事务、ACK 时点、异常分类和 Day 5/Day 6 交接边界。
  2. 配置只供导入消费者使用的手动 ACK Listener Container，明确并发数、prefetch 和测试环境启停开关。
  3. 新增导入消息 Listener/Handler，严格校验事件类型、版本、任务 ID 和消息 ID，不把消息体中的数据当作业务真源。
  4. 为 `ImportJobTransactionService` 增加“按任务 ID 读取持久化文件并处理”的入口，复用现有解析、校验、批量写入和终态统计，不复制一套导入规则。
  5. 保证 ACK 位于业务方法正常返回之后；业务事务抛出异常时 Listener 不 ACK，自动化测试必须验证调用顺序和失败行为。
  6. 先跑 Listener/业务聚焦测试，再跑真实 MySQL + RabbitMQ 异步导入验收和完整 `mvn clean test`。
  7. 清理验收任务、交易、行错误、文件、Outbox 和队列消息，释放临时应用端口，执行 `git diff --check` 和范围审计。
- **任务**：
  - [x] 新增 `docs/design/week4-day4-async-import-consumer-design.md`。
  - [x] 新增手动 ACK Listener Container 配置，并提供可测试的消费者启停配置。
  - [x] 新增 `IMPORT_REQUESTED` Listener/Handler，完成消息字段和类型校验。
  - [x] 从 `import_job_files` 按任务 ID 读取原始字节，校验文件记录与任务元数据一致。
  - [x] 复用 Week 3 导入逻辑完成 `PENDING → PROCESSING → SUCCESS / PARTIAL_SUCCESS / FAILED`。
  - [x] 确保数据库提交后才调用 `basicAck`；系统异常时事务回滚且不 ACK。
  - [x] 增加成功、部分成功、文件级失败、消息非法和系统异常的聚焦测试。
  - [x] 完成真实 HTTP `202` → RabbitMQ 消费 → GET 轮询终态 → MySQL 统计守恒的端到端验收。
  - [x] 运行完整回归、清理数据/消息/端口并完成提交前检查。
- **关键文件**：
  - `docs/design/week4-day4-async-import-consumer-design.md`
  - `src/main/resources/application.yml`
  - `src/main/java/com/finguard/core/messaging/consumer/`
  - `src/main/java/com/finguard/core/importjob/service/impl/ImportJobTransactionService.java`
  - `src/test/java/com/finguard/core/messaging/consumer/`
- **验收标准**：
  - 首次 ADMIN 上传仍返回 `202/PENDING`；真实消费者随后把任务推进到正确终态，GET 轮询无需依赖同步等待。
  - 合法文件、部分错误文件和文件级错误分别得到 `SUCCESS`、`PARTIAL_SUCCESS`、`FAILED`，任务计数与交易/行错误数据守恒。
  - Listener 只消费导入事件；错误事件类型、错误版本、非法任务 ID 或缺失文件不会执行导入，也不会产生交易副作用。
  - 自动化测试证明 `basicAck` 发生在业务方法成功返回之后；模拟系统异常时无 ACK、业务事务回滚且没有假成功状态。
  - 真实 RabbitMQ 队列可观察到消息被消费并确认，结束后消费者、待确认消息和验收消息均清零。
  - 聚焦测试和完整 `mvn clean test` 全部通过；MySQL/RabbitMQ/HTTP 验收完成，测试数据与临时端口清理，`git diff --check` 通过。
- **验收结论**：Day 4 聚焦测试 9/9、完整 `mvn clean test` 251/251 通过且无失败、错误或跳过；真实应用 `18080/actuator/health` 为 `200/UP`，首次 ADMIN multipart 上传返回 `202/PENDING + Location`，随后经 Outbox/RabbitMQ 消费轮询到 `SUCCESS|1|1|0`，MySQL 确认 1 条交易、1 份原始文件和 1 个 `SENT` 事件；重复文件返回 `200 duplicateFile=true` 并复用原任务。验收时导入队列 `ready=0/unacked=0`，结束后用户、账户、任务和交易残留均为 0，消费者和端口 `18080` 已释放。
- **学习重点**：`@RabbitListener`、Listener Container、手动 ACK、delivery tag、prefetch、数据库事务提交边界、业务错误与系统错误、消息载荷与数据库真源、异步任务轮询。
- **常见错误预防**：不要在业务事务提交前 ACK；不要把原始 CSV 放回消息；不要在 Listener 复制 Week 3 规则；不要用 `catch (Exception)` 后 ACK；不要在 Day 4 用无限重入主队列代替 Day 6 的有限重试和 DLQ。
- **回滚**：只回退 Day 4 新增的消费者、配置、测试和对导入 Service/Mapper 的最小改造；不修改或删除 V7。回滚后 HTTP 仍可可靠受理并发布消息，但导入任务会保持 `PENDING`，与 Day 3 基线一致。
- **提交**：`feat: add asynchronous import consumer`

### Week 4 Day 5：导入消费幂等、并发所有权与崩溃恢复

- **状态**：已完成
- **业务目标**：补齐“至少一次投递”下的导入消费可靠性：同一 `IMPORT_REQUESTED` 消息被重复发布、并发投递或在数据库已提交但 ACK 丢失后再次投递时，只允许一份业务结果；消费者在处理前或处理中崩溃后，任务能够重新被安全处理，不能永久卡在 `PROCESSING`。
- **实现前基线**：Day 4 已能从真实 RabbitMQ 消费导入消息，在一个数据库事务内读取持久化 CSV、推进任务状态、写入交易/行错误和终态统计，并在事务成功返回后手动 ACK；但终态重复消息、同任务并发消费、ACK 失败后的红投以及异常退出恢复尚未形成确定性行为。
- **目标流程**：
  ```text
  收到 IMPORT_REQUESTED
    → 校验消息并以 importJobId 查询数据库真源
    → 原子判断本次消费是否取得处理所有权
      → PENDING：唯一执行者处理导入
      → 已是终态：幂等返回，不重复写入
      → 正在处理：按已锁定的所有权/恢复规则处理，不盲目执行第二次
    → 业务事务提交后 ACK
    → 若提交后 ACK 丢失，红投读取终态并直接幂等 ACK
    → 若执行者崩溃，数据库回滚后由红投重新处理
  ```
- **范围边界**：
  - 本日只加强导入消费者，不实现对账消费者；对账消费者仍留给 Day 6。
  - 本日处理重复消息、同任务并发抢占、数据库提交后 ACK 丢失、消费者崩溃和 `PROCESSING` 恢复；业务规则、CSV 契约、文件哈希、交易唯一键和 HTTP `202 + Location` 语义保持不变。
  - 验证确认 Day 4 的 `PROCESSING` 不会独立提交，崩溃会整体回滚到 `PENDING`；本日使用任务行锁形成处理所有权，不增加没有实际作用的租约字段。
  - 本日没有新增 V8，也没有修改 V1～V7；未来若让 `PROCESSING` 跨事务可见，必须同时设计 token、租约、fencing 和有限延迟重试。
  - 两级 retry queue、重试次数、TTL 退避、非法消息隔离和 DLQ 属于 Day 6；本日不得用无限 `requeue=true` 或忙循环冒充崩溃恢复。
  - 不引入 Redis 分布式锁、Redisson、通用工作流框架、风险规则、人工审核、审计、监控或前端。
- **关键设计决定**：
  - 数据库任务状态是消费幂等真源，RabbitMQ `deliveryTag` 只用于当前 Channel ACK，不能作为跨投递幂等键。
  - 同一任务的所有权取得必须是数据库原子操作；不能先普通查询 `PENDING`，再无条件执行导入。
  - 终态 `SUCCESS / PARTIAL_SUCCESS / FAILED` 的重复消息必须返回“已处理”结果并 ACK，不抛出状态迁移异常形成毒消息。
  - 并发消费者只能有一个进入解析和写入阶段；未取得所有权的消费者不得新增交易、行错误或覆盖统计。
  - 数据库已提交但 `basicAck` 失败时，后续红投只能读取既有终态并 ACK；交易唯一键是最后防线，不能代替 Service 层幂等。
  - 消费者崩溃恢复必须有可证明的唯一策略：要么同一事务整体回滚到 `PENDING`，要么使用带 fencing token 的持久化所有权和过期恢复；禁止仅凭看到 `PROCESSING` 就永久 ACK。
- **执行顺序**：
  1. 新增 Day 5 设计文档，画出重复投递、并发抢占、ACK 丢失和崩溃恢复时序，先锁定状态决策表与事务边界。
  2. 为当前 `processPending` 增加故障与并发实验，确认 MySQL 行锁、事务回滚和 RabbitMQ 红投的真实行为，再决定是否需要 V8 token/lease。
  3. 把导入处理入口改为返回明确消费结果（首次取得、终态重复、正在处理/可恢复），让 Listener 只对可安全结束的结果 ACK。
  4. 实现数据库原子所有权、终态幂等短路和旧执行者防护；如采用租约，补齐过期判断、可注入 `Clock`、恢复条件和迁移约束。
  5. 增加重复顺序投递、至少两个消费者并发、提交后 ACK 失败、进程/事务异常和恢复重跑测试，逐项核对任务统计及数据库副作用。
  6. 使用真实 MySQL + RabbitMQ 完成 HTTP 上传、重复发布、并发消费、强制断开消费者、重启恢复和队列清零验收。
  7. 运行聚焦测试和完整 `mvn clean test`，清理任务、交易、行错误、原始文件、Outbox、RabbitMQ 消息和临时端口，执行 `git diff --check` 与范围审计。
- **任务**：
  - [x] 新增 `docs/design/week4-day5-import-consumer-idempotency-design.md`，锁定状态决策表、事务可见性、所有权和恢复策略。
  - [x] 用自动化实验覆盖当前单事务下的并发条件更新、回滚和终态可见性，并确认不需要 V8。
  - [x] 实现终态重复消息幂等短路，重复消费不再因 `PENDING → PROCESSING` 更新数为 0 而持续失败。
  - [x] 实现同一 `importJobId` 的数据库行锁所有权，两个消费者并发时只有一个执行业务逻辑。
  - [x] 实现数据库提交后 ACK 失败的红投恢复，第二次消费只 ACK、不新增任何业务记录。
  - [x] 实现消费者在处理前/处理中异常退出后的安全恢复，任务不会永久停留在 `PROCESSING`。
  - [x] 设计与测试证明单事务回滚足以恢复，因此没有增加无实际作用的 V8 token/lease。
  - [x] 完成聚焦、完整回归及真实 MySQL/RabbitMQ/HTTP 验收，并清理所有验收副作用。
- **关键文件**：
  - `docs/design/week4-day5-import-consumer-idempotency-design.md`
  - `src/main/java/com/finguard/core/messaging/consumer/importjob/`
  - `src/main/java/com/finguard/core/importjob/service/impl/ImportJobTransactionService.java`
  - `src/main/java/com/finguard/core/importjob/mapper/ImportJobMapper.java`
  - `src/test/java/com/finguard/core/messaging/consumer/importjob/`
- **验收标准**：
  - 同一消息顺序投递至少 2 次，最终只有 1 个任务、1 份正确的交易/行错误集合和一套守恒统计；第二次安全 ACK。
  - 至少两个真实消费者同时处理同一任务时，只有一个取得所有权并执行；另一消费者不产生业务副作用，任务最终处于唯一正确终态。
  - 注入“业务事务已提交、`basicAck` 抛出异常”后，RabbitMQ 红投不会重复写交易或行错误，最终消息可确认、队列 `ready=0/unacked=0`。
  - 注入消费者在取得任务后异常退出，重启或再次投递后任务能够完成；不存在永久 `PROCESSING`、旧执行者覆盖新结果或半套数据。
  - 对成功、部分成功、文件级失败三类终态都验证重复消费；任务计数、交易数、行错误数和文件哈希幂等关系保持不变。
  - 聚焦测试和完整 `mvn clean test` 全部通过；真实 MySQL/RabbitMQ/HTTP 验收、数据/消息/端口清理和 `git diff --check` 全部完成。
- **验收结论**：Day 5 聚焦测试 13/13、完整 `mvn clean test` 255/255 通过，零失败、零错误、零跳过。真实 Java 17 应用首次上传返回 `202/PENDING`；消费者关闭时主队列保留 1 条消息，重启后任务完成为 `SUCCESS|1|1|0`。应用再次停止后发布 2 条同任务重复消息，以 2 个消费者重启并发处理，最终仍只有 1 条交易、0 条行错误，队列 `ready=0/unacked=0`。验收用户、账户、任务、文件、Outbox、消息和端口 `18080` 已清理；当前没有残留 `PROCESSING` 任务。
- **学习重点**：至少一次投递、幂等与去重的区别、数据库条件更新、悲观行锁、事务可见性、RabbitMQ 红投标记、ACK 丢失窗口、fencing token、处理租约、崩溃恢复和数据库唯一约束兜底。
- **常见错误预防**：不要用内存 `Set` 记录已消费消息；不要把 `deliveryTag` 当业务幂等键；不要看到终态还重新解析文件；不要把更新数为 0 一律当系统异常；不要在没有 fencing 的情况下允许过期执行者继续提交；不要提前实现 Day 6 的无限重试或 DLQ。
- **回滚**：Listener/Handler/Service/Mapper 和测试可按本日范围回退；本日没有数据库迁移。回滚后恢复到 Day 4 的单次异步消费能力，已完成的任务和交易不得删除或重放。
- **提交**：`feat: make import consumption idempotent`

### Week 4 Day 6：异步对账消费者、有限重试与死信隔离

- **状态**：已完成
- **业务目标**：让 `RECONCILIATION_REQUESTED` 消息真正驱动自动对账，并为导入、对账两条消费链路建立统一但有边界的失败处理：确定性业务结果正常结束，临时系统故障最多延迟重试两次，非法或耗尽重试的消息进入对应 DLQ，不能无限重回主队列、静默丢失或重复写业务数据。
- **实现前基线**：Day 5 已完成异步导入、同任务行锁幂等、事务回滚恢复和提交后 ACK 丢失恢复；`RECONCILIATION_REQUESTED` 已由 Outbox 可靠发布到 `finguard.reconciliation.queue`，但没有消费者，因此新对账任务仍停留在 `PENDING`。当前导入 Listener 遇到非法消息或持续系统异常时只是不 ACK，尚无有限重试、退避和隔离闭环。
- **目标流程**：
  ```text
  RECONCILIATION_REQUESTED
    → 校验稳定消息契约，以 reconciliationJobId 查询数据库真源
    → 锁定对账任务并判断状态
      → PENDING / 可恢复 PROCESSING：复用 Week 3 匹配、结果写入和统计逻辑
      → COMPLETED / FAILED：幂等短路，不重复写结果
    → 同一数据库事务提交终态
    → 手动 ACK

  导入或对账消费失败
    → 已处理业务结果：提交终态并 ACK，不重试
    → 非法消息 / 不存在的任务：直接投递对应 DLQ，确认成功后 ACK 原消息
    → 临时系统故障：retry.1（5 秒）→ retry.2（30 秒）→ 回主队列重试
    → 两次重试后仍失败：安全标记现有非终态任务 FAILED → 投递 DLQ → ACK 原消息
  ```
- **范围边界**：
  - 本日实现对账消费者，并把有限重试/DLQ 同时接到导入和对账消费者；不只给其中一条链路做“演示版”重试。
  - 每条消息最多执行 1 次首次消费和 2 次延迟重试；第一版退避锁定为 5 秒、30 秒，禁止无限 `requeue=true`、零延迟忙循环和无上限指数退避。
  - 对账继续由 ADMIN 的 `POST /api/reconciliation-jobs` 显式触发；不在导入成功后自动创建对账任务，不新增人工重跑、删除任务或覆盖结果接口。
  - 保持现有 JSON 消息字段和稳定 `messageId`；重试次数使用受控消息头，RabbitMQ `x-death` 只用于诊断证据，不作为业务幂等主键。
  - 已处理的文件级业务失败、部分成功和对账确定性失败不盲目重试；非法消息、错误事件类型、错误版本、非法重试头和任务不存在直接隔离，且不得伪造业务任务。
  - 默认不新增 V8，不修改 V1～V7；优先复用任务状态、行锁和现有唯一约束。只有真实 SQL/并发测试证明现有结构不足时，才评审新的追加迁移。
  - 不引入 RabbitMQ 延迟消息插件、Redis、风险规则、人工审核、审计、监控、通用 MQ 框架或前端。
- **关键设计决定**：
  - 两条业务主队列各有 `retry.1`、`retry.2` 和 DLQ；retry queue 使用 TTL 到期后死信回原业务 exchange/主 routing key，DLQ 绑定统一 `finguard.dlx`。
  - 原始消息无重试头时按第 0 次处理；第一次临时失败写入 attempt=1 并送 retry.1，第二次写入 attempt=2 并送 retry.2，第三次失败进入 DLQ。重试不能修改消息体中的 `messageId`、事件类型、任务 ID、版本或创建时间。
  - 从主队列转交 retry queue/DLQ 时，必须先得到 Publisher Confirm ACK 且没有 mandatory return，之后才能 ACK 原消息；转交失败时原消息不得确认。即使“转交成功但原 ACK 丢失”造成重复投递，也由 Day 5 和本日的数据库幂等兜住。
  - 对账消费沿用数据库任务状态作为真源：同一任务并发消息通过行锁串行，`COMPLETED / FAILED` 直接幂等 ACK；`PENDING` 或可证明安全的遗留 `PROCESSING` 才进入业务处理。
  - 对账结果写入、统计守恒和终态更新必须在同一事务中完成；临时异常整体回滚，不得留下半套结果。重试耗尽时用独立短事务只把仍非终态的任务标记为 `FAILED`，不得覆盖已经提交的成功终态。
  - DLQ 保留原消息和安全的失败分类/重试次数，禁止写入 SQL、路径、密码、JWT、CSV 原文或堆栈；业务接口只暴露安全失败摘要，不暴露 RabbitMQ 内部细节。
- **执行顺序**：
  1. 新增 Day 6 设计文档，锁定两条链路的异常分类表、ACK/NACK/转交时序、重试头、TTL、DLX/DLQ 拓扑、对账事务和回滚边界。
  2. 扩展 RabbitMQ 声明与配置测试，建立导入/对账各两级 retry queue 和一个 DLQ，并验证 durable、binding、TTL、DLX 和 routing key 参数。
  3. 先实现共享的消费失败分类与可靠转交组件：保留稳定消息、增加受控 attempt 头、等待 Confirm/return 结果，再决定 ACK、重试或隔离。
  4. 改造导入 Listener 接入有限重试和 DLQ；保持 Day 5 的终态幂等、行锁和崩溃恢复行为不变。
  5. 新增对账 Listener/Handler/Container 配置，并重构最小对账事务入口，复用现有 Matcher、批量查询、结果写入和统计逻辑，补齐并发与终态幂等。
  6. 增加消息非法、任务不存在、确定性业务失败、临时故障后恢复、重试耗尽、转交失败、ACK 丢失和同任务并发测试，逐项核对消息去向及数据库副作用。
  7. 使用真实 MySQL + RabbitMQ + JWT + HTTP 完成异步导入、异步对账、两级退避、恢复成功和 DLQ 隔离验收，并检查主队列/retry queue/DLQ 的 `ready/unacked`。
  8. 运行聚焦测试和完整 `mvn clean test`，清理任务、结果、交易、行错误、文件、Outbox、所有测试消息和临时端口，执行 `git diff --check` 与范围审计。
- **任务**：
  - [x] 新增 `docs/design/week4-day6-retry-dlq-and-reconciliation-consumer-design.md`，完成异常决策表、消息时序和事务评审。
  - [x] 声明导入/对账各两级 TTL retry queue、对应 binding、统一 DLX 和各自 DLQ，并提供可校验的配置边界。
  - [x] 实现受控 retry attempt、失败分类和“可靠转交成功后再 ACK 原消息”的共享组件。
  - [x] 将导入消费者接入有限重试、非法消息隔离和重试耗尽处理，保持 Day 5 幂等语义不变。
  - [x] 实现对账消息校验、手动 ACK、任务行锁、终态幂等和单事务结果提交。
  - [x] 实现导入/对账重试耗尽后的安全失败恢复，不覆盖并发完成的终态，不伪造不存在的任务。
  - [x] 覆盖拓扑、路由、Listener、Handler、事务、并发、重试恢复、DLQ 和无副作用测试。
  - [x] 完成真实 HTTP/MySQL/RabbitMQ 端到端验收、完整回归、清理和提交前检查。
- **关键文件**：
  - `docs/design/week4-day6-retry-dlq-and-reconciliation-consumer-design.md`
  - `src/main/java/com/finguard/core/messaging/config/RabbitMessagingConfiguration.java`
  - `src/main/java/com/finguard/core/messaging/consumer/`
  - `src/main/java/com/finguard/core/reconciliation/service/impl/ReconciliationJobTransactionService.java`
  - `src/main/java/com/finguard/core/reconciliation/mapper/ReconciliationJobMapper.java`
  - `src/main/resources/application.yml`
  - `src/test/java/com/finguard/core/messaging/`
  - `src/test/java/com/finguard/core/reconciliation/`
- **验收标准**：
  - 真实 ADMIN 创建对账任务返回 `202/PENDING + Location`，Outbox 发布后由对账消费者推进到 `COMPLETED`；GET 查询的四类统计之和等于总数，结果数量与数据库一致。
  - 同一对账消息顺序或并发投递至少 2 次，只产生一套对账结果和一个正确终态；提交后 ACK 丢失的红投只做终态短路。
  - 导入和对账分别注入一次临时系统故障，消息经过 retry.1 延迟后可恢复成功；注入连续故障时严格经过 retry.1、retry.2，随后只进入对应 DLQ 一次，主队列不忙循环。
  - 非法消息、错误版本/事件类型、非法 attempt 头和不存在任务直接进入正确 DLQ，不调用业务 Service、不新增或修改任务、交易、行错误或对账结果。
  - retry/DLQ 转交失败时原消息不被 ACK；转交成功但原 ACK 失败后允许重复投递，但业务数据仍保持唯一。
  - 重试耗尽只把仍非终态的真实任务安全标记为 `FAILED` 并保存固定安全摘要；已完成任务不被覆盖，消息/DLQ 不包含敏感数据或内部异常。
  - 自动化测试验证两套 topology 的 queue arguments、TTL、DLX、routing key、持久化和消息头保持；真实 RabbitMQ 可观察到延迟和最终路由。
  - 聚焦测试和完整 `mvn clean test` 全部通过；真实 MySQL/JWT/HTTP/RabbitMQ 验收、数据/消息/端口清理和 `git diff --check` 全部完成。
- **验收结论**：Day 6 新增/扩展 32 个自动化用例，完整 `mvn clean test` 287/287 通过，零失败、零错误、零跳过。真实 Broker 测试分别验证导入和对账的一次临时失败经 5 秒 retry.1 恢复，以及持续失败严格经过 5 秒 retry.1、30 秒 retry.2 后安全标记 `FAILED` 并进入正确 DLQ；并发重复、非法 attempt、任务不存在、转交失败和 ACK 丢失均无重复业务副作用。真实 Java 17 应用健康为 `UP`，ADMIN JWT 登录后完成账户、人工交易、`202/PENDING → SUCCESS` 异步导入和 `202/PENDING → COMPLETED` 异步对账；MySQL 核对为 1 条导入交易、1 条对账结果和 2 条 `SENT` Outbox。验收用户、账户、任务、文件、交易、结果和消息已清理，8 个队列均为 `ready=0/unacked=0`，端口 `8080` 已释放。
- **学习重点**：RabbitMQ TTL、DLX/DLQ、消息红投与重发的区别、`x-death`、有限退避、异常分类、Publisher Confirm 与 Consumer ACK 的组合、毒消息隔离、至少一次投递下的幂等、数据库行锁和事务恢复。
- **常见错误预防**：不要 `basicNack(..., true)` 无限回主队列；不要用 `deliveryTag` 或 `x-death` 当业务幂等键；不要先 ACK 再发布 retry/DLQ；不要每次重试生成新 `messageId`；不要把业务校验失败当临时故障反复执行；不要在重试耗尽时覆盖已完成终态；不要为 Day 6 顺手加入管理后台或 Redis。
- **回滚**：可回退 Day 6 新增的 retry/DLQ 声明、失败路由组件、对账消费者、配置和测试，恢复 Day 5 行为；V1～V7、已完成任务、Outbox、交易和对账结果不得删除或重放。若队列参数已在本地 Broker 声明，回滚前只清理本项目明确命名的 Day 6 队列，不能删除 RabbitMQ 数据卷或其他队列。
- **提交**：`0015610 feat: add reconciliation consumer retries and DLQ`

### Week 4 Day 7：综合验收与周复盘

- **状态**：已完成
- **业务目标**：从可重建的干净环境出发，证明 Week 4 的异步导入与异步对账不仅能走通正常流程，还能在消息重复、Broker/消费者临时故障、重试耗尽和 ACK 丢失等场景下保持任务可恢复、业务数据唯一、失败消息可隔离，并形成可复核、可演示、可用于面试讲解的周验收证据。
- **当前基线**：Day 1～Day 6 已完成异步消息契约、RabbitMQ 基础拓扑、原始文件与 Outbox 持久化、可靠发布、导入/对账消费者、手动 ACK、任务行锁、终态幂等、两级有限重试和独立 DLQ；Day 6 完整测试基线为 287/287，但本日必须独立复验，不能直接把历史结论当作 Day 7 证据。
- **范围边界**：
  - 本日只做 Week 4 已实现能力的综合验收、缺陷核对、文档复盘、数据/消息清理和 Git 收口，不新增业务功能。
  - 不修改已应用的 V1～V7；不新增 Redis、风险规则、人工审核、乐观锁、审计日志、监控、前端或 Week 6 部署能力。
  - 若验收发现真实缺陷，先保存失败证据，再做最小范围修复并重新执行相关聚焦测试和完整回归；不得通过放宽断言、跳过测试或手工改库伪造通过。
  - 重建 Compose 数据卷前必须确认其中只有本项目可丢弃的测试数据；清理 RabbitMQ 时只操作本项目明确命名的 `finguard.*` 队列和消息，不删除其他项目资源。
- **执行顺序**：
  1. 核对 Day 1 契约、Day 2～Day 6 提交、当前代码和测试，建立“设计承诺 → 实际实现 → 验收证据”清单。
  2. 在确认数据可丢弃后重建项目专属 MySQL/RabbitMQ Compose 环境，等待两个容器健康，验证 Flyway V1→V7 和 RabbitMQ durable topology 可从空环境自动恢复。
  3. 先运行消息拓扑、Outbox、导入消费者、对账消费者、JWT/RBAC 和数据库基线聚焦测试，再运行完整 `mvn clean test`，记录用例总数、失败、错误和跳过数。
  4. 启动真实 Java 17 应用并轮询 `/actuator/health`，使用 ADMIN/REVIEWER JWT 完成账户、人工交易、`202 + Location` 异步导入、任务轮询、`202 + Location` 异步对账、结果查询、重复请求和权限矩阵验收。
  5. 在真实 Broker 上验证 Outbox 发布临时失败后按计划恢复为 `SENT`；分别验证导入和对账消费者的一次临时故障经 retry.1 恢复，以及持续故障严格经过 retry.1、retry.2 后进入各自 DLQ。
  6. 结合自动化测试和真实 MySQL/RabbitMQ 证据复核重复消息、并发投递、提交后 ACK 丢失、非法消息和不存在任务均不会产生重复交易、重复对账结果或错误终态。
  7. 按外键顺序清理验收用户、账户、任务、原始文件、交易、行错误、对账结果和 Outbox；记录证据后清空本项目测试消息，确认 8 个队列 `ready=0/unacked=0`，停止应用并释放 8080 端口。
  8. 新增 Week 4 复盘文档，更新 README、当前进度和 Git 里程碑索引，完成范围审计、敏感信息检查、`git diff --check` 和提交前复核。
- **任务**：
  - [x] 新增 `docs/review/week4-review.md`，按 Day 记录实际交付、提交、设计偏差、限制和 Week 5 边界。
  - [x] 验证干净 Compose 环境中 MySQL、RabbitMQ 均为 `healthy`，Flyway V1→V7 和 3 个 exchange、8 个 durable queue、bindings、TTL、DLX/DLQ 参数可自动重建。
  - [x] 完成消息、Outbox、导入、对账、认证和数据库聚焦测试，并执行完整 `mvn clean test`；总用例 287 个，零失败、零错误、零跳过。
  - [x] 使用真实 JWT/HTTP 跑通异步导入与异步对账正常链路，验证 `202 + Location`、终态轮询、重复请求复用、ADMIN/REVIEWER 权限和不存在资源响应。
  - [x] 验证 Outbox 在 Broker 临时不可用时不丢事件，恢复后使用稳定 `messageId` 发布并最终进入 `SENT`，不产生重复业务副作用。
  - [x] 对导入和对账分别验证一次临时故障恢复、持续故障两级退避与 DLQ 隔离，核对消息 headers、队列去向、任务安全失败摘要和主队列不忙循环。
  - [x] 复核重复/并发消息、ACK 丢失、非法消息、任务不存在和转交失败场景，确认任务、交易、行错误、对账结果与统计守恒。
  - [x] 完成数据库数据、Outbox、测试消息、临时凭据、应用进程和端口清理，确认没有提交 `.env`、JWT 密钥、RabbitMQ/MySQL 密码或内部异常堆栈。
  - [x] 更新 `TASKS.md`、`README.md` 和 Git 里程碑索引，执行范围审计与 `git diff --check`，形成 Week 4 可演示和面试复盘材料。
- **关键文件**：
  - `TASKS.md`
  - `README.md`
  - `docs/review/week4-review.md`
  - `docs/design/week4-day1-async-messaging-contract.md`
  - `docker-compose.yml`
  - `src/main/resources/application.yml`
  - `src/main/java/com/finguard/core/messaging/`
  - `src/test/java/com/finguard/core/messaging/`
  - `src/test/java/com/finguard/core/importjob/`
  - `src/test/java/com/finguard/core/reconciliation/`
  - `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`
- **验收标准**：
  - 从确认可清理的空数据卷启动后，MySQL 与 RabbitMQ 均为 `healthy`，应用健康为 `UP`，Flyway 最终版本为 7；3 个 exchange 和 8 个队列的 durable、binding、TTL、DLX、routing key 与代码配置一致。
  - 聚焦测试和完整 `mvn clean test` 全部通过；完整回归不少于 287 个用例，0 failures、0 errors、0 skipped，复盘文档记录实际数字而不是预填数字。
  - 真实 ADMIN 首次上传返回 `202/PENDING + Location`，轮询到 `SUCCESS` 或 `PARTIAL_SUCCESS`；首次对账返回 `202/PENDING + Location`，轮询到 `COMPLETED`，四类统计之和等于总数且结果行数与 MySQL 一致。
  - 相同文件和相同对账请求复用原任务；同一业务消息顺序或并发投递至少 2 次、提交后 ACK 丢失红投后，仍只有一套交易/行错误/对账结果和一个正确终态。
  - Outbox 在真实 Broker 临时不可用时保留可重试状态，Broker 恢复后以相同 `messageId` 可靠发布并进入 `SENT`；事件没有丢失，也没有重复业务写入。
  - 导入和对账的一次临时系统故障均经 retry.1 后恢复；持续故障均严格经过 retry.1、retry.2 并只进入对应 DLQ 一次，现有非终态任务安全标记为 `FAILED`，主队列无忙循环。
  - 非法版本/事件类型/attempt、任务不存在和转交失败的消息去向正确，不调用业务处理或污染 MySQL；REVIEWER 查询为 `200`、写入为 `403`，匿名请求为 `401`，不存在资源为 `404`。
  - 验收后相关 MySQL 业务数据和 Outbox 记录清零，本项目 8 个队列均为 `ready=0/unacked=0`，临时凭据未落盘，8080 端口已释放，`git diff --check` 和范围审计通过。
- **验收结论**：干净 Compose 环境自动恢复 Flyway V1～V7、3 个 exchange 和 8 个 durable queue；聚焦测试 56/56、完整 `mvn clean test` 287/287 通过，零失败、零错误、零跳过。真实 Java 17/JWT/HTTP 链路完成账户、人工交易、`202 + Location` 异步导入与对账、重复请求复用和 ADMIN/REVIEWER/匿名/不存在资源权限验收，导入结果为 2 行成功，对账结果为 matched=1、unmatched=1。RabbitMQ 停止期间 Outbox 保持 RETRY，恢复后同一消息进入 SENT 且任务成功；导入与对账的临时故障均可从 retry.1 恢复，持续故障进入 retry.2/DLQ，取出的 DLQ 消息均为 `attempt=2`、`RETRY_EXHAUSTED`，非终态任务安全标记 FAILED。数据库业务表、Outbox、验收用户和 8 个队列均已清零，8080 已释放，范围审计和 `git diff --check` 通过。详见 `docs/review/week4-review.md`。
- **学习重点**：用一条可解释的可靠性链路串起 HTTP `202`、数据库事务、Outbox、Publisher Confirm、mandatory return、持久化消息、手动 ACK、至少一次投递、幂等、TTL/DLX/DLQ、故障恢复和最终一致性；能够说明“消息不丢”和“业务不重复”分别由哪些机制保证，以及为什么 RabbitMQ 不能提供端到端 exactly-once。
- **常见错误预防**：不要只验 happy path；不要把“消息进队列”当作“业务已完成”；不要把 Outbox 重试和消费者 retry queue 混为一谈；不要用无限 `requeue=true` 制造忙循环；不要在记录 DLQ 证据前清空队列；不要用历史 287/287 代替本日实跑；不要为通过验收手工改任务终态或数据库统计；不要把 Week 5 功能混入收口提交。
- **回滚**：本日原则上只新增复盘证据和更新文档，不回退 Day 1～Day 6 已验证的异步实现。若本日尚未执行，回滚只需删除本节并恢复当前进度/路线表；若验收中产生临时数据或消息，按本项目表的外键顺序和 `finguard.*` 队列范围清理，不删除 V1～V7、不重放已完成业务任务、不删除 RabbitMQ 整个数据卷来掩盖问题。
- **提交建议**：`docs: complete week 4 acceptance review`

## 7. Week 5：风险识别、异常审核、Redis 与审计闭环

### Week 5 Day 1：业务契约、状态流转与数据模型设计

- **状态**：已完成
- **业务目标**：在编写代码前锁定“对账完成 → 风险识别 → 异常进入审核 → REVIEWER 确认或忽略 → 关键操作留痕”的业务闭环，同时确定 Redis 统计缓存与登录/上传限流的最小边界。
- **当前基线**：
  - Week 4 已在提交 `c7a1156` 收口；当前真实能力是 V1～V7、异步导入/对账、Outbox、手动 ACK、终态幂等、两级有限重试和独立 DLQ。
  - Week 4 验收基线为完整 `mvn clean test` 287/287；进入 Day 1 时必须重新运行，不能直接把历史数字当作本日证据。
  - 当前代码中没有 `risk`、`review`、`audit`、`statistics` 或 `ratelimit` 业务模块，Docker Compose 中也没有 Redis；这些都是本周计划，不得描述为已实现。
- **目标请求流**：

  ```text
  对账任务到达 COMPLETED
    → 读取 reconciliation_results 与相关 CSV_IMPORT 交易
    → 运行最多三条可解释风险规则
    → 幂等保存 risk_hits
    → 为对账异常或风险命中幂等生成 PENDING review_tasks
    → REVIEWER 查询任务并携带 version 提交 CONFIRMED / IGNORED
    → 业务状态与 audit_logs 在同一事务中提交
    → statistics 聚合 MySQL 真源，Redis 只缓存结果

  登录 / CSV 上传请求
    → Redis 原子限流判断
    → 允许时进入现有认证或导入流程
    → 超限返回 429 + Retry-After
  ```

  该流程是 Day 1 要评审并锁定的目标，不代表仓库当前已经实现。
- **范围边界**：
  - 本日只新增设计文档并更新任务入口，不新增依赖、Docker 服务、Flyway 迁移、Java 业务代码、配置项或接口。
  - 不修改已应用的 V1～V7，不预建空模块，不写 TODO 业务骨架，不添加 Redis 容器来冒充设计完成。
  - 不引入 Drools、规则 DSL、规则版本平台、复杂评分模型、通用工作流/状态机、Redis 分布式锁、Token 黑名单、前端或 Week 6 监控部署能力。
  - MySQL 继续是业务真源；Redis 只用于统计缓存和限流，不能承担审核幂等、最终一致性或业务状态真源职责。
- **执行顺序**：
  1. 核对 `PROJECT_BRIEF.md`、Week 4 复盘、V6/V7 迁移、对账完成事务、现有权限和错误响应，建立“已有事实 / Week 5 计划”清单。
  2. 定义业务术语：风险命中、对账异常、审核任务、确认、忽略、审计事件、统计快照和限流窗口，避免同一词在不同模块含义不同。
  3. 画出正常链路、无风险链路、重复执行、规则异常、并发审核、审计失败、Redis 未命中和 Redis 不可用八类时序。
  4. 逐条评审三条风险规则的输入数据、阈值、窗口、原因码、规则启停、幂等键和边界样例。
  5. 锁定审核任务生成矩阵、状态机、版本字段、权限矩阵、HTTP 契约和并发冲突语义。
  6. 设计 `risk_hits`、`review_tasks`、`audit_logs` 的 ER 图、字段、约束、索引、外键、唯一键和 V8～V10 追加迁移顺序。
  7. 锁定统计接口、Redis key/TTL/序列化/失效策略，以及登录和上传限流的 Key、窗口、阈值、原子性与降级行为。
  8. 定义五类审计事件的触发点、操作者、目标对象、安全摘要、事务边界和重复执行语义。
  9. 形成 Day 2～Day 6 的文件计划与验收矩阵，逐项确认没有把后续实现提前塞入 Day 1。
  10. 完成设计评审、完整回归、敏感信息检查、`git diff --check` 和提交前范围复核。
- **任务**：
  - [x] 新增 `docs/design/week5-day1-risk-review-redis-audit-contract.md`，包含业务上下文、术语表、模块边界、请求流、事务边界和失败路径。
  - [x] 定义 `LargeAmountRule`：评审对象、金额阈值、`BigDecimal` 比较方式、启用开关、稳定规则/原因码、阈值快照和命中/不命中样例。
  - [x] 定义 `DuplicateTransactionRule`：与现有交易唯一键、文件内重复、对账 `DUPLICATE` 的职责差异，避免把同一事实重复建模三次。
  - [x] 定义 `FrequentTransactionRule`：账户维度、方向是否参与、时间窗口、次数阈值、边界时刻和批量查询方案，禁止逐条 N+1 查询。
  - [x] 锁定风险命中幂等键，候选为 `(reconciliation_job_id, csv_transaction_id, rule_code)`；明确重复 MQ、任务重跑和并发评估只产生一条命中。
  - [x] 建立审核任务生成矩阵：`UNMATCHED`、`DUPLICATE`、`SUSPICIOUS` 和三类风险命中分别是否生成任务、来源字段为何、如何防止重复。
  - [x] 评审审核来源的关系模型；优先使用可验证外键和“恰好一个来源”的检查约束，避免只存 `source_type + source_id` 导致数据库无法保证引用存在。
  - [x] 定义审核状态 `PENDING → CONFIRMED / IGNORED`、期望 `version`、条件更新、非法迁移、重复提交、并发冲突和统一 `409` 错误码。
  - [x] 锁定审核接口和权限矩阵：列表、详情、确认、忽略、分页/筛选参数，以及 ADMIN、REVIEWER、匿名、无权角色和不存在资源的响应。
  - [x] 设计 `risk_hits`、`review_tasks`、`audit_logs` 的字段类型、金额精度、时间、外键删除策略、唯一约束、检查约束、查询索引和 V8～V10 迁移顺序。
  - [x] 解决审计操作者模型：用户动作关联 `users.id`，异步系统动作保留原请求发起人或明确 `SYSTEM`，并用约束防止二者同时缺失或冲突。
  - [x] 定义审计白名单：CSV 上传、导入失败、对账完成、审核确认、审核忽略；明确成功/失败何时落库以及重复消息不重复写审计的规则。
  - [x] 定义 `GET /api/statistics/overview` 的最小响应字段、授权、MySQL 聚合真源、Redis key、TTL、缓存未命中、提交后失效和 Redis 故障降级。
  - [x] 定义登录/上传限流的 Key 维度、阈值、固定窗口、原子操作、`429 + Retry-After`、窗口恢复和 Redis 故障策略；Key 不包含密码、JWT 或可直接枚举的敏感值。
  - [x] 制定自动化测试矩阵：纯规则单测、数据库约束、任务幂等、事务回滚、并发审核、权限、审计脱敏、缓存一致性、限流原子性和 Redis 降级。
  - [x] 明确 Day 2～Day 6 的设计文档、迁移、模块、接口和测试文件清单，并为每个 Day 保留独立回滚边界。
  - [x] 运行完整 `mvn clean test`，记录实际用例数、失败、错误和跳过数；执行 `git diff --check` 和范围审计。
- **关键文件**：
  - `TASKS.md`
  - `PROJECT_BRIEF.md`
  - `README.md`
  - `docs/design/week5-day1-risk-review-redis-audit-contract.md`
  - `docs/review/week4-review.md`
  - `src/main/resources/db/migration/V6__create_reconciliation_tables.sql`
  - `src/main/resources/db/migration/V7__create_import_file_and_outbox_tables.sql`
  - `src/main/java/com/finguard/core/reconciliation/service/impl/ReconciliationJobTransactionService.java`
  - `src/main/java/com/finguard/core/importjob/service/impl/ImportJobTransactionService.java`
  - `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`
  - `src/main/resources/application.yml`
  - `docker-compose.yml`
- **验收标准**：
  - 设计文档包含目标、非目标、现状、术语、请求流、异常流、模块依赖、事务边界、状态机、权限矩阵、错误码、ER 图、索引依据、Redis 契约、审计矩阵和测试矩阵。
  - 三条风险规则分别具有唯一职责、确定输入、可配置阈值、稳定规则/原因码、命中与不命中样例；不会与交易唯一键或对账分类职责混淆。
  - `risk_hits`、`review_tasks`、`audit_logs` 的候选结构能够使用真实外键、唯一约束、检查约束和版本字段保护引用、幂等与并发正确性。
  - 审核任务生成矩阵没有歧义；同一来源不会重复建任务；两个审核人基于同一版本提交时只有一个允许成功，失败方为稳定 `409`。
  - 权限矩阵明确到每个接口；`401`、`403`、`404`、`409`、`429` 的责任层和数据库副作用均有测试入口。
  - 五类审计事件逐项明确触发点、actor、target、result、summary、事务和幂等语义；设计中没有密码、JWT、原始 CSV、SQL、内部异常或堆栈字段。
  - Redis 设计能说明缓存穿透/击穿/雪崩在本项目的最小处理、统计缓存如何失效、限流如何保持原子、Redis 不可用时如何降级，以及为什么 Redis 不是最终正确性保证。
  - Day 2～Day 6 的实现顺序、关键文件和验收边界可直接执行；Day 1 Git 变更只包含 `TASKS.md` 和 Day 1 设计文档，不出现 Java、SQL、依赖、配置或 Docker 改动。
  - 完整 `mvn clean test` 不少于当前 287 项基线，0 failures、0 errors、0 skipped；`git diff --check`、敏感信息检查和范围审计通过。
- **验收结论**：Day 1 设计评审完成，三条风险规则、审核状态机与权限、V8～V10 候选模型、五类审计事件、统计缓存、固定窗口限流、Redis fail-open 和 Day 2～Day 6 边界均已锁定。完整 `mvn clean test` 实跑 287/287，0 failures、0 errors、0 skipped；MySQL 8.4.10 与 RabbitMQ 4.3.4 均为 healthy。变更仅包含 `TASKS.md` 与本日设计文档，未新增 Java、SQL、依赖、配置或 Docker 服务；敏感信息检查、范围审计和 `git diff --check` 通过。
- **学习重点**：策略模式与责任链的取舍、领域状态与数据库约束、乐观锁、幂等键、事务一致性、缓存旁路模式、缓存失效、固定窗口限流、Redis 原子操作、审计不可变性，以及 `401/403/404/409/429` 的边界。
- **常见错误预防**：不要在 Day 1 写代码或迁移；不要把对账 `SUSPICIOUS` 与风险命中混为一张表；不要用无外键的通用 `source_id` 草率建模；不要用 Redis 锁替代数据库乐观锁；不要缓存进行中的任务详情；不要让审核状态和审计日志分属两个无法保证一致的提交；不要把明文用户名、密码、JWT、CSV 或异常堆栈写进 Redis key/审计摘要；不要预填测试通过数字。
- **回滚**：Day 1 原则上只有任务清单和设计文档；回滚时只删除本日新增设计文档并恢复本节，不删除 V1～V7、Week 4 代码、MySQL/RabbitMQ 数据卷或现有测试。若评审中产生临时图表或实验文件，只清理本项目明确创建且可再生的临时文件。
- **提交**：`docs: design week 5 risk review redis and audit contract`

### Week 5 Day 2：风险命中持久层与规则执行骨架

- **状态**：已完成
- **业务目标**：建立可追踪、可去重、可解释的风险命中 MySQL 真源，并定义稳定的规则输入/输出契约，使 Day 3 能在不改表、不推翻接口的前提下实现三条具体规则和审核任务生成。
- **当前基线**：
  - Week 5 Day 1 已在提交 `71b432d` 完成契约设计；进入 Day 2 时的完整回归基线为 287/287，实际执行后必须记录本日新数字，不能复制历史结果。
  - 进入 Day 2 时 Flyway 最新版本为 V7，共 11 张业务表；`reconciliation_results` 已通过真实外键唯一关联本次对账任务和 CSV 交易，因此 V8 只引用 `reconciliation_results.id`，不重复保存 job/transaction 外键。
  - 进入 Day 2 时仓库没有 `risk` 包、`risk_hits` 表、风险规则实现或审核任务；本日只建立持久化与契约基础，不把计划描述成现有能力。
- **本日数据流**：

  ```text
  测试或后续编排层提供已持久化的 ReconciliationResult + CSV 交易
    → 组装只读 RiskEvaluationContext
    → 按统一 RiskRule 契约计算 Optional<RiskRuleResult>
    → 无命中：不创建空记录
    → 有命中：转换为 RiskHit
    → 批量写入 risk_hits
    → 按 reconciliation_result_id 批量回查持久化结果
  ```

  Day 2 只证明这条“契约 → 持久化”路径可用；不接入现有 `ReconciliationJobTransactionService`，不随真实 RabbitMQ 对账消息自动执行。
- **范围边界**：
  - 只新增 Day 2 设计文档、V8、风险枚举/实体/Mapper、不可变规则契约模型及对应单元/集成测试。
  - 不实现 `LargeAmountRule`、`DuplicateTransactionRule`、`FrequentTransactionRule`、`RiskProperties` 或 `RiskEvaluationService`；具体规则、批量候选查询和对账事务接入属于 Day 3。
  - 不新增 `review_tasks`、审核接口、乐观锁、审计日志、统计接口、Redis、限流、RabbitMQ 拓扑或消息字段。
  - 不新增 Controller、HTTP 路由或权限规则，不修改 `ReconciliationJobTransactionService`，不改变现有导入/对账运行行为。
  - 不修改已应用的 V1～V7；V8 一旦应用只能由后续追加迁移演进，不能回写 V8 或通过删除共享数据卷冒充回滚。
- **执行顺序**：
  1. 重新核对 Day 1 的规则/原因码、字段形状、索引与 Day 3 接入需求，形成 `week5-day2-risk-persistence-and-rule-engine-design.md`。
  2. 先写数据库约束与持久层失败测试，明确合法三种记录形状、非法组合、重复命中、未知外键和删除限制的预期结果。
  3. 新增 V8，仅创建 `risk_hits`；同步更新 Flyway 最新版本、业务表数量和 schema 约束断言。
  4. 建立 `RiskRuleCode`、`RiskReasonCode`、`RiskHit` 和 `RiskHitMapper`，实现批量插入及按一批对账结果 ID 稳定回查，避免给 Day 3 留下逐条 SQL 的接口。
  5. 定义不可变的 `RiskRule`、`RiskEvaluationContext`、`RiskRuleResult`；锁定空值、正数、金额 scale、规则/原因匹配和固定安全摘要边界，但不写具体规则实现。
  6. 运行规则模型单测、风险持久层集成测试和数据库基线测试；在真实 MySQL 上检查表、列、CHECK、FK、唯一键、索引及 `EXPLAIN`。
  7. 执行完整 `mvn clean test`、应用健康检查、测试数据/端口清理、敏感信息检查、范围审计和 `git diff --check`。
  8. 回填实际测试数字与验收结论，确认没有 Day 3～Day 6 能力后再提交。
- **关键设计决定**：
  - `risk_hits` 只保存 `reconciliation_result_id`；通过该结果可追溯到 reconciliation job 和 CSV transaction，避免三份外键产生关系不一致。
  - 幂等最终由 `uk_risk_hits_result_rule(reconciliation_result_id, rule_code)` 保证；Java 预检查只能改善错误表达，不能替代唯一约束，也不使用 `INSERT IGNORE` 吞掉其他数据错误。
  - 三组规则/原因固定配对：`LARGE_AMOUNT/AMOUNT_AT_OR_ABOVE_THRESHOLD`、`POSSIBLE_DUPLICATE/SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME`、`FREQUENT_TRANSACTION/EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD`。
  - 大额记录只填 `observed_amount/threshold_amount`；重复与高频记录只填 `observed_count/threshold_count/window_seconds`。所有已填金额、次数、阈值和窗口必须为正，观测值必须达到对应阈值。
  - `reason_summary` 只能由服务端固定模板产生，最长 255 字符，不保存交易描述、原始 CSV、配置 JSON、SQL、异常消息或堆栈。
  - `RiskRule` 只计算并返回 `Optional<RiskRuleResult>`，不得访问 Mapper、开启事务、写风险命中、创建审核任务或修改交易/对账结果。
  - 规则顺序使用显式 order 或 `RiskRuleCode` 固定次序保证 `LARGE_AMOUNT → POSSIBLE_DUPLICATE → FREQUENT_TRANSACTION`；顺序仅用于确定性输出，不表示覆盖关系，一个结果允许命中多条不同规则。
  - Mapper 提供批量写入和批量回查；Day 2 不创建空 `Service` 包装 `BaseMapper`，事务编排统一留给 Day 3 的 `RiskEvaluationService`。
- **任务**：
  - [x] 新增 Day 2 设计文档，逐项列出业务目的、请求/数据流、V8 DDL、Java 契约、测试矩阵、失败路径、回滚和 Day 3 接口。
  - [x] 新增 `V8__create_risk_hit_table.sql`，包含主键、`reconciliation_result_id` RESTRICT 外键、三组规则/原因与字段形状 CHECK、正数 CHECK、唯一幂等键和规则统计索引。
  - [x] 将全部最新 Flyway 版本断言更新为 8、业务表数量更新为 12，并验证 V8 表、外键、唯一键、CHECK 和索引真实存在。
  - [x] 新增 `RiskRuleCode`、`RiskReasonCode`，禁止自由字符串进入持久层，并提供稳定的规则/原因匹配关系。
  - [x] 新增 `RiskHit` 实体，金额使用 `BigDecimal`，时间使用 `LocalDateTime` 映射 MySQL `DATETIME(3)`，不使用 `double` 或 Java 原生序列化对象。
  - [x] 新增 `RiskHitMapper`，支持非空列表批量插入、按一批 `reconciliationResultId` 回查，并按固定业务规则次序提供确定性结果。
  - [x] 新增 `RiskRule`、`RiskEvaluationContext`、`RiskRuleResult`；使用只读模型表达当前交易、对账结果、批量预加载候选、业务时区和命中快照，不暴露可变集合。
  - [x] 为规则契约模型增加快速失败校验：必填 ID/交易/时区、金额 scale 不超过 2、数值为正、规则与原因/快照字段形状一致、摘要非空且不超长。
  - [x] 新增 `RiskHitPersistenceIntegrationTest`：覆盖三种合法形状往返、批量插入/回查、相同结果不同规则可共存、相同结果同规则唯一冲突、未知结果外键、结果删除 RESTRICT 和全部非法 CHECK 组合。
  - [x] 新增规则契约单测：覆盖命中/无命中返回形状、不可变候选集合、规则顺序、金额精度、空值与非法快照拒绝；不伪造三条具体规则的业务边界测试。
  - [x] 使用真实 MySQL 从空库执行 V1→V8，核对 V1～V7 checksum 不变，并以 `EXPLAIN` 证明批量回查和规则筛选命中预期索引。
  - [x] 运行风险聚焦测试、数据库基线测试和完整 `mvn clean test`，记录实际 total/failures/errors/skipped；启动应用验证 `/actuator/health` 为 `UP`，随后清理数据和监听端口。
  - [x] 执行敏感信息检查、Day 3～Day 6 范围审计和 `git diff --check`，回填验收证据后提交。
- **关键文件**：
  - `docs/design/week5-day2-risk-persistence-and-rule-engine-design.md`
  - `src/main/resources/db/migration/V8__create_risk_hit_table.sql`
  - `src/main/java/com/finguard/core/risk/entity/RiskHit.java`
  - `src/main/java/com/finguard/core/risk/mapper/RiskHitMapper.java`
  - `src/main/java/com/finguard/core/risk/model/RiskRuleCode.java`
  - `src/main/java/com/finguard/core/risk/model/RiskReasonCode.java`
  - `src/main/java/com/finguard/core/risk/rule/RiskRule.java`
  - `src/main/java/com/finguard/core/risk/rule/RiskRuleResult.java`
  - `src/main/java/com/finguard/core/risk/rule/RiskEvaluationContext.java`
  - `src/test/java/com/finguard/core/risk/RiskHitPersistenceIntegrationTest.java`
  - `src/test/java/com/finguard/core/risk/rule/RiskRuleContractTest.java`
  - `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`
  - `src/test/java/com/finguard/core/auth/AuthDatabaseIntegrationTest.java`
  - `src/test/java/com/finguard/core/importjob/ImportJobPersistenceIntegrationTest.java`
  - `src/test/java/com/finguard/core/reconciliation/support/ReconciliationTestFixture.java`
- **验收清单**：
  - [x] Day 2 设计文档与 Day 1 契约一致，且能直接指导 V8、Java 模型、Mapper 和测试实现。
  - [x] 干净 MySQL 从 V1→V8 成功，V1～V7 未修改，12 张业务表及 V8 全部约束/索引可由 `information_schema` 复核。
  - [x] 三种合法风险命中均可往返持久化；非法 rule/reason/shape/数值由 Java 模型或 MySQL 约束拒绝，且不残留半套数据。
  - [x] 同一对账结果可保存不同规则命中；同一结果与同一规则重复写入被唯一键拒绝，只保留一条真源记录。
  - [x] 未知 `reconciliation_result_id` 不能写入，存在风险证据的对账结果不能删除；真实删除规则为 `RESTRICT`。
  - [x] 批量 Mapper 路径不存在逐结果查询/写入的 N+1，返回顺序稳定，真实 `EXPLAIN` 使用预期索引。
  - [x] `RiskRule` 契约是纯计算、输入输出只读且金额精确；无命中不创建空 `risk_hits`，一个结果允许多个不同规则命中。
  - [x] 聚焦测试、数据库基线测试和完整 `mvn clean test` 均为本日实跑，0 failures、0 errors、0 skipped。
  - [x] 应用健康检查为 `UP`，验收数据和端口已清理，敏感信息检查、范围审计与 `git diff --check` 通过。
- **验收结论**：Day 2 风险持久层与规则契约已完成。风险/数据库聚焦测试 18/18、包含历史版本断言的扩展聚焦测试 30/30、完整 `mvn clean test` 297/297 通过，均为 0 failures、0 errors、0 skipped。独立空库从 V1→V8 成功，12 张业务表存在且 V1～V7 的 7 个 checksum 全部一致；真实 `EXPLAIN` 分别使用 `uk_risk_hits_result_rule` 和 `idx_risk_hits_rule_created_id`。真实应用在临时 256-bit JWT 密钥与 18080 端口下返回 `HTTP 200 {"status":"UP"}`；风险测试数据、临时验收库、RabbitMQ 消息和端口均已清理。
- **学习重点**：
  - 必须掌握：追加式 Flyway 迁移、外键/唯一键/CHECK 的职责差异、`BigDecimal` 精度、MyBatis 批量 SQL、策略接口、不可变输入输出和数据库最终幂等。
  - 边做边学：规则/原因码配对约束、合法数据形状建模、批量回查、稳定排序、`information_schema` 与 `EXPLAIN` 验证。
  - 留到 Day 3：类型安全规则配置、三条具体算法、时间窗口、滑动窗口、批量候选查询、风险评估事务和审核任务生成。
  - 本日不学不做：审核 HTTP/乐观锁、审计、Redis 缓存/限流、规则 DSL、Drools、机器学习评分或 Week 6 监控部署。
- **常见错误预防**：不要修改 V6/V7 给风险表“腾位置”；不要重复保存 jobId/transactionId 制造三份关系；不要用 `INSERT IGNORE` 隐藏非预期约束错误；不要只在 Java 校验而缺少数据库兜底；不要用 `double` 或无 scale 边界的金额；不要让规则对象访问 Mapper；不要为接口而创建空 Service；不要把 Day 3 的具体规则、对账事务接入或 `review_tasks` 偷跑进本日；不要把历史 287/287 当作 Day 2 验收数字。
- **回滚**：Java、测试和设计文档可按本日文件范围回退；若 V8 只应用在确认可重建的项目测试库，可随测试环境重建回到 V7。若 V8 已进入共享或需保留数据的环境，不得修改/删除已应用迁移，只能新增补偿迁移；回滚不得删除已有对账结果、MySQL/RabbitMQ 数据卷或 Week 4 能力。
- **提交**：`feat: add risk persistence and rule foundation`

### Week 5 Day 3：三条风险规则与审核任务生成

- **状态**：已完成
- **业务目标**：让每次成功对账在同一 MySQL 事务内完成“结果落库 → 三条规则评估 → 风险命中落库 → 异常/风险转待审核任务 → 对账任务进入 `COMPLETED`”，且每条命中可解释、重复执行无重复副作用、任一步失败整体回滚。
- **进入本日时的基线**：
  - Week 5 Day 2 已在提交 `0cbb71a` 完成；最新 Flyway 为 V8，共 12 张业务表，已有 `risk_hits`、三组规则/原因枚举、不可变 `RiskRule` 输入输出与批量持久化 Mapper。
  - Day 2 的实际回归基线为 297/297，0 failures、0 errors、0 skipped；这只是进入 Day 3 的历史证据，Day 3 必须重新实跑并记录新数字。
  - 进入 Day 3 时仍没有三条具体规则、`RiskProperties`、`RiskEvaluationService`、`review_tasks` 或 `review` 模块；对账事务是保存结果后直接将 job 置为 `COMPLETED`。
- **本日数据流**：

  ```text
  RabbitMQ 对账消费者锁定 PENDING / PROCESSING job
    → 计算并批量保存 reconciliation_results
    → 按 jobId 一次回查已持久化结果及真实 ID
    → 按账户 + 总时间边界分批预加载历史 CSV_IMPORT 候选
    → 为每个结果组装 RiskEvaluationContext
    → 按 LARGE_AMOUNT → POSSIBLE_DUPLICATE → FREQUENT_TRANSACTION 执行
    → 批量保存 risk_hits
    → 为三类对账异常和每条 risk_hit 生成 PENDING review_tasks
    → 计数校验后将 reconciliation_job 置为 COMPLETED
    → 事务提交后消费者才 ACK
  ```
- **范围边界**：
  - 只实现类型安全规则配置、三条纯计算规则、批量候选查询、风险评估编排、V9/审核任务持久层、任务生成器和对账事务接入。
  - Day 3 不提供 `review` Controller、HTTP 详情/分页/决策接口，不修改 RBAC；审核查询、`PENDING → CONFIRMED / IGNORED`、乐观锁和权限矩阵统一留给 Day 4。
  - 不新增风险独立消息、RabbitMQ 拓扑或 Outbox 事件；风险评估是已有对账消费事务的一部分。
  - 不实现审计、Redis、限流、动态规则平台、Drools、DSL、机器学习评分、审核撤销/重开或 Week 6 能力。
  - 不修改已应用的 V1～V8；V9 一旦应用只能由后续追加迁移演进。未经真实 `EXPLAIN` 证明不新增“猜测型”索引。
- **执行顺序**：
  1. 根据 Day 1 契约和 Day 2 真实代码完成 `week5-day3-risk-rules-and-review-task-design.md`，先锁定查询边界、事务归属、幂等冲突语义和测试矩阵。
  2. 先为三条规则写失败单测，再实现 `RiskProperties`、配置绑定/启动校验和三个规则类，保持规则无 Mapper、无事务、无副作用。
  3. 新增历史 CSV 交易的账户/时间范围批量查询，以一次预加载 + 内存分组/滑动窗口代替逐交易 SQL。
  4. 实现 `RiskEvaluationService`，稳定组装 context、跳过关闭规则、将命中转换为 `RiskHit`、分批写入并批量回查真实命中 ID；无命中时不执行空 INSERT。
  5. 以持久层集成测试驱动 V9 和 `review` 模型/Mapper，验证来源二选一、`PENDING(version=0)` 状态形状、三个 RESTRICT 外键、两个唯一键及查询索引。
  6. 实现 `ReviewTaskGenerator`：为 `UNMATCHED/DUPLICATE/SUSPICIOUS` 每条结果建一条异常任务，为每条 `risk_hit` 建一条风险任务；`MATCHED` 且无风险时不建任务。
  7. 将结果回查、风险评估、风险命中、审核任务和 job `COMPLETED` 接入 `ReconciliationJobTransactionService` 的同一事务，保留现有失败分类、有限重试、DLQ、红投短路和提交后 ACK 语义。
  8. 先跑规则、V9、编排/回滚和对账消费聚焦测试，再跑完整 `mvn clean test`；最后做真实 MySQL/RabbitMQ/HTTP 闭环、数据清理、范围审计、敏感信息检查和 `git diff --check`。
- **关键设计决定**：
  - 大额规则默认启用，阈值 `10000.00` CNY，`amount >= threshold` 即命中；金额使用 `BigDecimal.compareTo`，不使用 `double`。
  - 疑似重复规则默认 5 分钟含边界；只统计同账户、同方向、精确同金额且 `(transaction_time, id)` 早于当前交易的 CSV 候选，只让排序靠后的交易命中。
  - 高频规则默认 10 分钟含两端、同账户至少 5 笔 `EXPENSE`；第 5 笔及之后命中，同时刻用 ID 确定先后，历史候选可跨导入任务。
  - 候选只查未删除 `CSV_IMPORT`，按账户分批并用本批最早/最晚交易加最大规则窗口形成总时间边界；具体命中由纯规则再过滤，禁止 N+1。
  - 自定义多值 INSERT 不依赖批量回填自增 ID；对账结果按 jobId 回查，风险命中按 resultIds 回查，两条路径都必须稳定排序。
  - `review_tasks` 使用 `reconciliation_result_id` 与 `risk_hit_id` 两个真实可空外键，由 CHECK 保证二选一；不用无法建立引用完整性的通用 `source_type + source_id`。
  - 同一对账结果可同时产生 1 个对账异常任务和最多 3 个风险任务；它们表达不同事实，不互相覆盖。
  - `uk_risk_hits_result_rule`、`uk_review_tasks_reconciliation_result` 和 `uk_review_tasks_risk_hit` 是最终幂等防线；不使用 `INSERT IGNORE` 或吞掉未分类 `DuplicateKeyException`。正常重复 MQ 应在终态短路中无副作用返回。
  - 规则计算、命中落库、任务生成或计数不一致时必须抛出并回滚；不能留下部分 `risk_hits/review_tasks` 后仍把 job 标成 `COMPLETED`。
- **任务**：
  - [x] 新增 Day 3 设计文档，明确三条规则算法、配置校验、批量查询、V9 DDL、生成矩阵、事务/幂等/失败语义、测试、回滚和 Day 4 接口边界。
  - [x] 新增并启用类型安全 `RiskProperties`；校验大额阈值为正且 scale 不超过 2，两个时间窗口可转为正整数秒，高频阈值为正，并支持三条规则独立启停。
  - [x] 实现 `LargeAmountRule`、`DuplicateTransactionRule`、`FrequentTransactionRule`，只输出固定原因码、阈值/窗口快照和不含敏感文本的安全摘要。
  - [x] 为对账结果和历史 CSV 交易增加必要的批量查询，账户 ID 按固定上限分批，不引入逐结果/逐规则 SQL；用真实 `EXPLAIN` 判断现有索引是否足够。
  - [x] 实现 `RiskEvaluationService`，保证规则顺序、关闭规则跳过、无命中不写库、一个结果可保存多条不同规则命中，且批量写入数与候选命中数一致。
  - [x] 新增 `V9__create_review_task_table.sql`，不修改 V1～V8；同步更新最新 Flyway 版本、业务表数、表结构、外键、CHECK、唯一键和索引基线断言。
  - [x] 建立 `ReviewTaskSourceType`、`ReviewTaskStatus`、`ReviewTask`、`ReviewTaskMapper` 和批量写入/按来源批量回查能力；Day 3 不新增 Controller、查询 DTO 或决策 Service。
  - [x] 实现 `ReviewTaskGenerator`，按生成矩阵转换对账异常与风险命中，插入前后都校验候选数/实际数，所有新任务固定为 `PENDING(version=0)`。
  - [x] 修改 `ReconciliationJobTransactionService`，使对账结果、风险命中、审核任务和对账终态同事务提交；重复终态消息不重跑规则，失败时由现有重试/DLQ 链路接管。
  - [x] 新增三条规则单测：阈值上下界、5 分钟/5 分 1 毫秒、10 分钟边界、第 4/5 笔、同时刻 ID 次序、账户/方向/金额隔离、规则关闭和非法配置。
  - [x] 新增 V9/生成器集成测试：两种合法来源形状、全部非法状态/来源组合、未知外键、RESTRICT、单来源唯一、异常 + 多规则共存和稳定回查。
  - [x] 新增事务与消费集成测试：无风险正常结果、三类对账异常、单/多规则命中、历史跨导入窗口、重复/并发消息、ACK 丢失红投、中途异常整体回滚和重试恢复。
  - [x] 运行聚焦测试和完整 `mvn clean test`；使用真实 JWT/HTTP 触发异步导入与对账，用 MySQL 核对 result/hit/review 数量和 job 终态，然后清理验收数据、RabbitMQ 消息、临时凭据和端口。
  - [x] 回填 Day 3 实际测试数字、迁移/索引证据、真实闭环结果、已知限制与验收结论；执行范围审计、敏感信息检查和 `git diff --check` 后再提交。
- **关键文件**：
  - `docs/design/week5-day3-risk-rules-and-review-task-design.md`
  - `src/main/resources/db/migration/V9__create_review_task_table.sql`
  - `src/main/resources/application.yml`
  - `src/main/java/com/finguard/core/risk/config/RiskProperties.java`
  - `src/main/java/com/finguard/core/risk/rule/`
  - `src/main/java/com/finguard/core/risk/service/RiskEvaluationService.java`
  - `src/main/java/com/finguard/core/review/`
  - `src/main/java/com/finguard/core/reconciliation/mapper/ReconciliationResultMapper.java`
  - `src/main/java/com/finguard/core/reconciliation/service/impl/ReconciliationJobTransactionService.java`
  - `src/main/java/com/finguard/core/transaction/mapper/TransactionMapper.java`
  - `src/test/java/com/finguard/core/risk/`
  - `src/test/java/com/finguard/core/review/`
  - `src/test/java/com/finguard/core/reconciliation/`
  - `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`
- **验收清单**：
  - [x] 三条规则的命中、不命中、边界、关闭和非法配置均有确定性测试；金额、次数、阈值、窗口和原因摘要可从 `risk_hits` 解释。
  - [x] 候选加载为账户分批查询 + 内存过滤/时间窗口，不存在逐交易 N+1；已运行真实 `EXPLAIN` 并记录暂不新增索引的证据。
  - [x] 干净 MySQL 可从 V1→V9，V1～V8 checksum 不变；13 张业务表以及 V9 全部列、三个外键、CHECK、两个唯一键和查询索引均可复核。
  - [x] `MATCHED` 且无风险不创建任务；三类异常结果各创建 1 条异常任务；每条风险命中各创建 1 条风险任务，且全部为 `PENDING(version=0)`。
  - [x] 重复/并发 MQ、重复评估和 ACK 丢失红投不增加第二组 result/hit/review 副作用；唯一键仍作为最终防线。
  - [x] 注入规则异常、风险写入失败或审核任务写入失败时，results/hits/review/job 终态整体回滚；现有重试/DLQ 语义没有被绕过。
  - [x] 聚焦测试、数据库基线、完整 `mvn clean test` 和真实 MySQL/RabbitMQ/JWT/HTTP 闭环均为本日实跑，0 failures、0 errors、0 skipped，不复制 Day 2 的 297/297。
  - [x] Day 4～Day 6 的审核决策、HTTP/RBAC、审计、Redis 和限流未偷跑；验收数据、消息、临时凭据和端口已清理，`git diff --check` 通过。
- **验收结论**：
  - 规则/配置/契约聚焦测试 13/13，V9/数据库基线聚焦测试 13/13，风险事务/并发/回滚聚焦测试 10/10，扩大聚焦回归 67/67，全部 0 failures、0 errors、0 skipped。
  - 完整 `mvn clean test` 为 314/314，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - 独立临时数据库从空库执行 V1→V9，最新版本 9，共 13 张业务表；V1～V8 的 8 个 checksum 与当前项目库全部一致。
  - V9 真实验证 3 个 RESTRICT 外键、4 个 CHECK、2 个来源唯一键和 2 个分页索引；非法来源/状态形状、未知外键、重复来源和证据删除均被拒绝。
  - 4,000 条合成交易上的真实 `EXPLAIN` 估算时间窗口命中 15.89%，MySQL 选择全表扫描；该数据规模与选择性下新索引收益未被证明，因此 Day 3 不追加猜测型索引，留到 Week 6 用更真实数据规模压测。
  - 真实应用在 18080 端口返回 `UP`；ADMIN JWT/HTTP 异步导入为 `SUCCESS|5|5`，对账为 `COMPLETED|5|0|5|0|0`，SQL 证明 5 条 results、10 条 hits（大额 5/疑似重复 4/高频 1）和 15 条 `PENDING(version=0)` reviews（异常 5/风险 10）。
  - 验收用户、账户、交易、results、hits、reviews、临时数据库和验收脚本已清理；8 个 RabbitMQ 业务队列 ready/unacked 全为 0，18080 无监听。
- **学习重点**：
  - 必须掌握：策略模式、`BigDecimal.compareTo`、开闭时间区间、稳定排序、滑动窗口、批量 SQL、事务原子性和数据库唯一键幂等。
  - 边做边学：`@ConfigurationProperties` 类型安全配置/校验、多规则组合编排、多值 INSERT 后回查 ID、真实外键二选一建模、MQ 红投与业务幂等的区别。
  - 留到 Day 4：分页/详情 HTTP 查询、REVIEWER 决策、条件更新乐观锁、`409` 冲突分类和审核权限矩阵。
  - 本日不学不做：Drools/规则 DSL、机器学习风控、Redis 锁、新 MQ 事件、通用工作流引擎、审计与监控部署。
- **常见错误预防**：不要让规则直接查库/写库；不要对每笔交易跑三次 SQL；不要用系统当前时间代替交易时间窗口；不要把 5 分钟/10 分钟含边界写成开区间；不要让同时刻的大 ID 交易提前影响小 ID；不要依赖批量自增 ID 回填；不要把对账异常任务和风险任务去重成一条；不要用 `INSERT IGNORE` 掩盖数据形状错误；不要在 Day 3 提前实现审核决策、RBAC、审计或 Redis。
- **回滚**：Java、测试、配置和设计文档可按 Day 3 文件范围回退。V9 若仅应用于明确可重建的项目测试库，可随该测试环境重建回到 V8；若已进入共享或需保留数据的数据库，不得修改/删除 V9，只能新增补偿迁移。回滚不得删除已有导入/对账数据、MySQL/RabbitMQ 数据卷或 Week 4/Day 2 能力来掩盖失败。
- **提交**：`feat: add risk evaluation and review task generation`

### Week 5 Day 4：异常审核接口与乐观锁并发控制

- **状态**：已完成
- **业务目标**：把 Day 3 已生成的 `PENDING(version=0)` 审核任务开放为可查询、可解释且只能决策一次的人工审核流程；允许 REVIEWER 确认或忽略，同时保证两个审核人基于同一版本并发提交时只有一个状态迁移成功。
- **当前基线**：
  - Week 5 Day 3 已在提交 `5128247` 完成；当前真实能力是三条风险规则、V8 风险命中、V9 审核任务、任务幂等生成和对账事务原子接入，没有审核 Controller、查询/决策 Service 或新权限规则。
  - 数据库最新版本为 V9，共 13 张业务表；`review_tasks` 已有两种真实来源外键、`PENDING/CONFIRMED/IGNORED` 状态 CHECK、`version`、审核人/时间/说明字段和两个分页索引。
  - 进入本日的完整回归历史基线是 314/314；Day 4 完成后必须重新实跑并记录新数字，不能直接复制该结果。
- **请求流**：

  ```text
  ADMIN / REVIEWER 查询审核列表或详情
    → 按 status / sourceType / resultType / ruleCode 筛选
    → REVIEWER 读取 PENDING(version=0)
    → PATCH decision 携带 CONFIRMED/IGNORED + expected version + optional note
    → UPDATE ... WHERE id=? AND status='PENDING' AND version=?
    → 更新 1 行：version+1，保存 reviewer/time/note，返回最新任务
    → 更新 0 行：分类为不存在、非法终态或版本冲突
  ```
- **范围边界**：
  - 本日只实现 `GET /api/review-tasks`、`GET /api/review-tasks/{id}`、`PATCH /api/review-tasks/{id}/decision`，以及查询投影、条件更新、统一错误和权限矩阵。
  - 审核只改变 `review_tasks`；不修改 `transactions`、`reconciliation_results` 或 `risk_hits`，不重跑对账/规则，不产生新 RabbitMQ 消息。
  - 不修改已应用的 V1～V9，不预先新增 V10；现有索引先用真实 `EXPLAIN` 验证，禁止根据单个小样本追加猜测型索引。
  - 不使用 `synchronized`、本地锁、Redis 分布式锁或悲观长事务，不实现撤销、重开、转派、认领、批量审核、多级审批或通用工作流。
  - Day 4 不写审计日志；只保留清晰的事务接入点，Day 5 再保证审核终态与 `REVIEW_CONFIRMED/REVIEW_IGNORED` 审计同事务提交。
  - 不实现 Redis、统计、限流、前端或 Week 6 监控/部署能力。
- **关键设计决定**：
  - 列表复用现有 `PageResponse`，page 默认 1、size 默认 20 且最大 100；固定按 `created_at DESC, id DESC` 排序，避免并列时间导致翻页抖动。
  - `resultType` 只适用于异常来源，`ruleCode` 只适用于风险来源；二者互斥。来源省略时可以从专属条件推导，矛盾组合返回 `400 INVALID_REQUEST`，不静默返回空页。
  - 详情/列表通过只读 JOIN 投影解释两种来源；风险任务经 `risk_hit` 关联展示 CSV 交易和对账结果，但不把派生关系伪装成 `review_tasks` 的第二个来源外键。
  - 决策使用独立 `ReviewDecision(CONFIRMED/IGNORED)`，不允许客户端提交 `PENDING`；reviewerId 只从已验签 JWT subject 获取，不能由请求体指定。
  - note trim 后空白保存为 `null`，最多 255 个 Unicode 字符；`reviewedAt/updatedAt` 使用现有 `businessClock`，保证测试确定性。
  - 真正的乐观锁是单条条件更新：同时匹配 id、`PENDING` 和 expected version，并检查受影响行数；不能使用 `selectById → 修改 Entity → 无条件 updateById`。
  - 请求开始时已是终态返回 `409 INVALID_REVIEW_OPERATION`；请求版本已过期或合法预检查后输掉并发竞争返回 `409 REVIEW_VERSION_CONFLICT`；不存在返回 `404 REVIEW_TASK_NOT_FOUND`。
- **执行顺序**：
  1. 先按 Day 1 契约和 V9 真实结构锁定 DTO、筛选组合、响应字段、状态机、错误码与并发失败语义，并把失败测试写在实现之前。
  2. 扩展 `ReviewTaskMapper`，实现详情/分页 JOIN 投影和条件更新；用真实 MySQL 覆盖两种来源、稳定排序、过滤组合和 0/1 更新行数。
  3. 实现 `ReviewTaskService` 的只读查询、note 规范化、状态/版本预检查、原子条件更新和更新后回查。
  4. 实现 Controller 三个路由，复用现有分页与统一错误响应；增加三类审核异常和 `ErrorCode`/全局映射。
  5. 显式更新 `SecurityConfiguration`：GET 允许 ADMIN/REVIEWER，PATCH decision 仅允许 REVIEWER，并覆盖所有身份组合的零副作用测试。
  6. 使用两个真实事务和同步屏障并发提交同一 version=0 任务，证明恰好一个成功、一个稳定 409，成功方字段不会被覆盖。
  7. 运行聚焦测试、数据库基线和完整回归；启动真实应用，使用 ADMIN/REVIEWER JWT 完成 HTTP/MySQL 验收。
  8. 清理验收数据、消息、临时凭据和端口，执行敏感信息检查、范围审计、`git diff --check`，回填实际证据后再提交。
- **任务**：
  - [x] 新增 `docs/design/week5-day4-review-workflow-and-optimistic-lock-design.md`，完成本日业务目标、接口、查询、条件更新、冲突分类、权限、测试、回滚与 Day 5 交接安排；不把计划描述成已实现功能。
  - [x] 新增 `ReviewTaskQueryRequest`，实现 page/size 默认与边界、status/sourceType/resultType/ruleCode 类型安全筛选和来源专属条件组合校验。
  - [x] 新增独立 `ReviewDecision` 与 `ReviewDecisionRequest`；只接受 CONFIRMED/IGNORED、非负 expected version 和 trim 后最多 255 字符的可选 note。
  - [x] 新增只读查询投影与 `ReviewTaskResponse`，为异常来源和风险来源返回稳定、可解释且不含敏感原文的统一响应。
  - [x] 扩展 `ReviewTaskMapper`，实现详情查询、稳定分页 JOIN 查询和按 id+PENDING+version 的条件更新；禁止无条件实体覆盖。
  - [x] 实现 `ReviewTaskService`/实现类，完成查询、详情、决策人提取后的业务校验、note 规范化、固定时钟、条件更新、回查与 0 行冲突分类。
  - [x] 新增 `ReviewTaskNotFoundException`、`InvalidReviewOperationException`、`ReviewVersionConflictException`，扩展统一错误码与 `404/409` 全局异常响应。
  - [x] 实现三个审核 HTTP 接口；成功决策返回 `200` 和最新任务，不使用 204，不允许请求体覆盖 reviewerId、reviewedAt 或 version 结果。
  - [x] 更新 Spring Security：查询允许 ADMIN/REVIEWER，决策只允许 REVIEWER；匿名/无效 Token 为 401，ADMIN 决策和无角色访问为 403。
  - [x] 新增 DTO/Service/Controller 测试，覆盖默认分页、非法参数、两种来源响应、确认/忽略、note 边界、不存在、终态、过期版本和拒绝路径零副作用。
  - [x] 新增真实 MySQL Mapper/事务集成测试，覆盖筛选、稳定分页、V9 CHECK、条件更新 0/1 行、终态不可变和失败回滚。
  - [x] 新增真实并发测试：两个事务读取同一 version=0 后同时提交，断言一个成功、一个 `REVIEW_VERSION_CONFLICT`，最终只有成功方的状态、审核人、时间和说明。
  - [x] 扩展 RBAC 集成测试，使用真实签名 JWT 覆盖 ADMIN/REVIEWER/匿名/无角色在三个路由上的完整权限矩阵及数据库副作用。
  - [x] 使用真实 `EXPLAIN` 验证 V9 分页索引；运行聚焦测试、数据库基线、完整 `mvn clean test` 和真实 JWT/HTTP/MySQL 闭环，记录本日实际数字。
  - [x] 清理验收数据、RabbitMQ 消息、临时凭据和监听端口；执行敏感信息检查、Day 5/6 范围审计和 `git diff --check` 后提交。
- **关键文件**：
  - `docs/design/week5-day4-review-workflow-and-optimistic-lock-design.md`
  - `src/main/java/com/finguard/core/review/controller/ReviewTaskController.java`
  - `src/main/java/com/finguard/core/review/dto/ReviewTaskQueryRequest.java`
  - `src/main/java/com/finguard/core/review/dto/ReviewDecisionRequest.java`
  - `src/main/java/com/finguard/core/review/model/ReviewDecision.java`
  - `src/main/java/com/finguard/core/review/model/ReviewTaskView.java`
  - `src/main/java/com/finguard/core/review/service/ReviewTaskService.java`
  - `src/main/java/com/finguard/core/review/service/impl/ReviewTaskServiceImpl.java`
  - `src/main/java/com/finguard/core/review/vo/ReviewTaskResponse.java`
  - `src/main/java/com/finguard/core/review/exception/`
  - `src/main/java/com/finguard/core/review/mapper/ReviewTaskMapper.java`
  - `src/main/java/com/finguard/core/common/exception/ErrorCode.java`
  - `src/main/java/com/finguard/core/common/exception/GlobalExceptionHandler.java`
  - `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`
  - `src/test/java/com/finguard/core/review/`
  - `src/test/java/com/finguard/core/auth/security/RbacAuthorizationIntegrationTest.java`
- **验收标准**：
  - 三个接口符合 Day 1 契约；两种审核来源能正确解释，分页按 `created_at DESC, id DESC` 稳定且 JOIN 不放大 count。
  - 条件更新同时包含 id、PENDING 和 expected version，成功只能影响 1 行；终态为 version=1，reviewer/time 非空且不能再变更。
  - 真实 MySQL 并发实验恰好一个 200、一个 `409 REVIEW_VERSION_CONFLICT`，最终数据库只保留成功方决策。
  - 不存在、终态、过期版本分别为稳定 404/409；400/401/403/404/409 全部与数据库零副作用证据一致。
  - ADMIN/REVIEWER 查询、仅 REVIEWER 决策、匿名 401、ADMIN 决策/无角色 403 的权限矩阵完整通过。
  - 聚焦、数据库基线、完整回归和真实 JWT/HTTP/MySQL 验收均为本日实跑；数据、消息、凭据和端口清理完成，`git diff --check` 通过。
  - 变更中没有迁移修改、V10 审计、Redis、限流、新 MQ、撤销/重开、批量审核或通用工作流实现。
- **验收结论**：
  - Day 4 审核/RBAC/数据库聚焦测试 32/32，完整 `mvn clean test` 326/326，均为 0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - 真实 MySQL 两事务并发和真实双 HTTP 并发均证明同一 version=0 只有一个成功；HTTP 得到一个 200、一个 `409 REVIEW_VERSION_CONFLICT`，最终 version=1 且成功方的状态、审核人、时间和说明没有被覆盖。
  - 真实应用在 18080 返回 `UP`；真实 ADMIN/REVIEWER 登录签发 JWT 后，列表/详情为 200、匿名为 401、ADMIN 决策为 403、过期版本为 409、REVIEWER 决策为 200、终态重复提交为 409。
  - 真实 SQL 确认 `CONFIRMED|1|reviewer|note|reviewed_at` 状态形状；`EXPLAIN` 对 status 分页使用 `idx_review_tasks_status_created_id`，source+status 小样本也由优化器选择该覆盖索引，本日不新增猜测型迁移。
  - 注入决策后回查失败时，事务把任务完整恢复为 `PENDING|0|NULL|NULL|NULL`；400/401/403/404/409 拒绝路径均有零副作用证据。
  - 验收用户、账户、导入/对账/审核数据和临时凭据已清理，8 个 RabbitMQ 业务队列 ready/unacked 全为 0，18080 已释放；MySQL 8.4.10 与 RabbitMQ 4.3.4 均为 healthy。
- **学习重点**：
  - 必须掌握：乐观锁三件套（期望版本、条件更新、受影响行数）、原子状态迁移、短事务、稳定分页、JOIN 投影、统一异常和 401/403/404/409 分工。
  - 边做边学：两个真实事务的并发测试、失败后的冲突分类、JWT subject 作为审核人、固定 `Clock`、数据库 CHECK 与 Service 校验的双层保护。
  - 留到 Day 5：不可变审计表、审核决策与审计同事务、审计失败回滚和 ADMIN 审计查询。
  - 本日不学不做：Redis 锁、悲观长事务、工作流引擎、撤销/重开、多级审批、前端状态管理和监控部署。
- **常见错误预防**：不要把 version 只返回给客户端却不放进 UPDATE WHERE；不要使用 `updateById` 覆盖并发结果；不要用 Java/Redis 锁代替数据库条件更新；不要允许 ADMIN 自动拥有 REVIEWER 专属决策权；不要信任请求体里的审核人/时间；不要让 JOIN 导致分页总数重复；不要把重复终态提交伪装成成功；不要修改 V9 或提前写 Day 5 审计。
- **回滚**：Day 4 不新增迁移；代码、测试、权限规则和设计文档可按本日文件范围回退。回滚后 V9 中已有 `PENDING` 任务继续保留，不删除审核、风险、对账、交易或消息数据来掩盖失败。
- **提交**：`feat: add optimistic exception review workflow`

### Week 5 Day 5：关键业务操作审计日志

- **状态**：已完成
- **业务目标**：新增 V10 `audit_logs` 作为关键业务审计真源，让 CSV 首次受理、导入失败、对账完成、审核确认和审核忽略具备可追溯证据；业务状态与审计必须在同一事务一起提交或一起回滚，同时不泄漏敏感数据或把普通查询写成海量日志。
- **当前基线**：
  - Week 5 Day 4 已在提交 `286391f` 完成；当前真实能力是 V9、13 张业务表、审核查询/决策、REVIEWER 专属写权限和条件更新乐观锁，仓库尚无 V10、`audit` 模块或审计接口。
  - 进入本日的完整回归历史基线为 326/326；实现完成后必须重新实跑并记录新数字，不能复制历史结果。
- **目标链路**：

  ```text
  业务事务执行关键状态变化
    → 由类型安全的 AuditLogService 生成白名单事件和固定安全摘要
    → INSERT audit_logs
    → 业务事实 + 审计证据一起 COMMIT
    → 任一写入失败则整个事务 ROLLBACK

  ADMIN GET /api/audit-logs
    → 按 actionCode / initiatedBy 筛选
    → created_at DESC, id DESC 稳定分页
  ```
- **范围边界**：
  - 只审计 `CSV_UPLOAD_ACCEPTED`、`IMPORT_FAILED`、`RECONCILIATION_COMPLETED`、`REVIEW_CONFIRMED`、`REVIEW_IGNORED` 五类动作。
  - 不审计普通查询、健康检查或每一行 CSV 错误；不做通用 AOP 全量拦截、复杂合规平台、历史回填或审计修改/删除/导出接口。
  - 审计写入加入现有业务事务，不使用 `REQUIRES_NEW`；`IMPORT_FAILED` 表示任务合法进入 FAILED 终态，不单独记录导致业务事务整体回滚的未知异常。
  - 不修改已应用的 V1～V9；不引入 Redis、统计、限流、新 RabbitMQ 事件、前端或 Week 6 监控部署能力。
  - 不记录密码、JWT、原始 CSV、文件名/hash、交易描述、完整审核 note、SQL、自由异常消息或堆栈。
- **关键设计决定**：
  - `audit_logs` 保存 `action_code`、`actor_type`、`actor_user_id`、`initiated_by`、`outcome`、三选一 target、固定 `summary` 和 `created_at`；不增加 `updated_at`。
  - USER actor 必须有用户外键且等于 initiatedBy；异步导入/对账以 SYSTEM 为 actor、原请求 `created_by` 为 initiatedBy，不能伪造成当前线程登录用户。
  - 三个 target 外键恰好一个非空并与 action 匹配；FK 全部 RESTRICT，CHECK 保护 actor/target/outcome/summary 形状，action+target 唯一键提供最终幂等防线。
  - `AuditLogMapper` 使用受限 insert/select，不继承暴露 update/delete 的通用 Mapper；本项目承诺应用层 append-only，不夸大为独立 WORM 防篡改系统。
  - 安全摘要只由固定服务端模板产生；对账摘要仅包含守恒计数，导入失败不拼接 `error_summary`，审核摘要不拼接 decision note。
  - 正常幂等优先复用文件唯一键、任务行锁、终态短路和审核条件更新；不使用 `INSERT IGNORE` 吞掉 CHECK/FK/未知唯一冲突。
- **执行顺序**：
  1. 新增 Day 5 设计文档并先写 V10 约束/失败测试，锁定五类合法形状和非法 actor/target/outcome 行为。
  2. 新增 V10；把最新 Flyway 版本更新为 10、业务表数量更新为 14，并在真实 MySQL 核对 CHECK、FK、唯一键、RESTRICT 和索引。
  3. 建立 audit 枚举、实体、受限 Mapper、Service、查询 DTO/VO 和固定摘要，完成持久层与分页测试。
  4. 接入首次 CSV 上传事务；证明 audit 失败会回滚 job/file/Outbox，重复文件不重复审计。
  5. 接入解析失败、全行失败、处理异常和重试耗尽四类 FAILED 路径；证明重复 MQ/ACK 丢失红投没有重复审计。
  6. 接入对账完成事务；证明 results、risk hits、review tasks、job COMPLETED 和 completed audit 原子提交/回滚。
  7. 接入审核决策事务；证明 confirm/ignore 正确映射、失败/冲突无审计、审计失败恢复 PENDING、两个并发请求只有成功方一条审计。
  8. 实现 ADMIN 只读 `GET /api/audit-logs`，完成 actionCode/initiatedBy 筛选、稳定分页、参数校验和 401/403 零副作用测试。
  9. 运行聚焦测试、数据库基线、完整回归和真实 MySQL/RabbitMQ/JWT/HTTP 验收；清理数据、消息、凭据和端口。
  10. 执行敏感信息检查、Day 6 范围审计和 `git diff --check`，回填实际数字与验收结论后再提交。
- **任务**：
  - [x] 新增 `docs/design/week5-day5-audit-log-design.md`，明确现状、五类事件、表结构、事务接入点、查询/RBAC、测试、回滚和学习边界。
  - [x] 新增 `V10__create_audit_log_table.sql`，实现五类 action、USER/SYSTEM actor、initiatedBy、SUCCESS/FAILED、三选一真实 target、固定摘要和毫秒时间。
  - [x] 增加 action/actor/outcome/target/summary CHECK、五个 RESTRICT FK、三组 action+target 唯一键及 action/initiatedBy 分页索引。
  - [x] 更新全部最新 Flyway 版本和表数量断言，使用真实 MySQL 从 V1→V10 验证 V1～V9 checksum 不变，并用 `EXPLAIN` 检查查询索引。
  - [x] 建立 `audit` 模块、白名单枚举、受限 Mapper 与类型安全写入 Service；写方法使用 `Propagation.MANDATORY` 加入现有事务，不暴露自由动作字符串或 update/delete 能力。
  - [x] 首次 CSV 上传在 job/file/Outbox 同事务写 `CSV_UPLOAD_ACCEPTED`；重复文件仍只保留一条 accepted。
  - [x] 所有首次进入 FAILED 的导入路径同事务写 `IMPORT_FAILED`；重复消费、终态短路和恢复不重复。
  - [x] 对账 results/hits/tasks/job COMPLETED 同事务写 `RECONCILIATION_COMPLETED`，固定摘要计数与 job 字段一致。
  - [x] 审核条件更新成功后同事务写 `REVIEW_CONFIRMED/REVIEW_IGNORED`；0 行更新、404/409、重复终态和失败方不写审计。
  - [x] 新增 `GET /api/audit-logs`，复用 `PageResponse`，支持 actionCode/initiatedBy 筛选并按 `created_at DESC, id DESC` 排序；只允许 ADMIN。
  - [x] 覆盖五类动作、非法数据库形状、FK/唯一键/RESTRICT、业务与审计双向回滚、重复消息、并发审核和敏感信息屏蔽测试。
  - [x] 完成真实 JWT/HTTP/MySQL/RabbitMQ 验收，记录聚焦与完整测试数字，清理 audit-first 数据、队列、临时凭据和端口。
  - [x] 执行敏感信息检查、范围审计和 `git diff --check`；实现完成后才更新 README 能力与本节验收结论。
- **关键文件**：
  - `docs/design/week5-day5-audit-log-design.md`
  - `src/main/resources/db/migration/V10__create_audit_log_table.sql`
  - `src/main/java/com/finguard/core/audit/`
  - `src/main/java/com/finguard/core/importjob/service/impl/ImportJobTransactionService.java`
  - `src/main/java/com/finguard/core/reconciliation/service/impl/ReconciliationJobTransactionService.java`
  - `src/main/java/com/finguard/core/review/service/impl/ReviewTaskServiceImpl.java`
  - `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`
  - `src/test/java/com/finguard/core/audit/`
  - `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`
  - `src/test/java/com/finguard/core/auth/security/RbacAuthorizationIntegrationTest.java`
- **验收标准**：
  - 五类关键动作各产生正确且唯一的 actor/initiatedBy/target/outcome/summary；重复文件、重复 MQ、ACK 丢失红投和重复审核不重复。
  - 首次上传、导入失败、对账完成和审核决策均与审计同事务；审计失败时对应业务状态与副作用完整回滚，业务失败时不留下假成功审计。
  - 两个并发审核仍为一个成功、一个 `409 REVIEW_VERSION_CONFLICT`，数据库只有成功方一条终态审计。
  - `/api/audit-logs` 分页/筛选稳定；ADMIN 200、REVIEWER/无角色 403、匿名/无效 JWT 401，拒绝路径零写副作用。
  - V10 的 CHECK、FK、UNIQUE、RESTRICT、索引和真实 `EXPLAIN` 证据完整；V1～V9 未修改。
  - 响应与数据库摘要不包含密码、JWT、原始 CSV、文件名/hash、交易描述、完整审核 note、SQL、异常消息或堆栈。
  - 聚焦、数据库基线、完整 `mvn clean test` 和真实 MySQL/RabbitMQ/JWT/HTTP 均为本日实跑；清理、范围审计和 `git diff --check` 通过。
  - 变更中没有 Redis、统计、限流、新 MQ、通用 AOP 审计、历史回填或审计更新/删除接口。
- **验收结论**：V10 已在真实 MySQL 8.4.10 应用，Flyway 最新版本为 10、业务表为 14 张，V1～V9 checksum 保持不变；五个 RESTRICT 外键、七个 CHECK、三组 action+target 唯一键和两个分页索引均已验证，`EXPLAIN` 分别命中 action 与 initiatedBy 索引。聚焦测试按阶段为 15/15、21/21、17/17、16/16、10/10；完整 `mvn clean test` 实跑 344/344，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。真实应用健康为 `UP`，主导入 `SUCCESS`、错误文件 `FAILED`，重复上传/重复对账复用原任务；对账计数为 total=3、matched=1、unmatched=2；顺序审核完成 confirm/ignore，并发同版本请求稳定得到一个 200、一个 409，三个成功决策各只有一条审计。审计查询验证 ADMIN 200、REVIEWER 403、匿名 401、非法分页 400，actor/outcome/target 形状 0 异常；验收数据、8 个队列、临时凭据和 18080 端口均已清理。
- **学习重点**：
  - 必须掌握：append-only 审计、同事务一致性、actor 与 initiatedBy、固定安全摘要、数据库 CHECK/FK/UNIQUE/RESTRICT 和业务幂等分工。
  - 边做边学：失败注入回滚、重复消息终态短路、受限 Mapper、稳定分页、真实 `EXPLAIN` 和 ADMIN 专属 RBAC。
  - 留到 Day 6：Redis 统计缓存、提交后失效、固定窗口登录/上传限流与 fail-open。
  - 本日不学不做：WORM/哈希链/数字签名、SIEM、事件溯源、通用 AOP、跨服务分布式事务和前端审计台。
- **常见错误预防**：不要用 `REQUIRES_NEW` 让审计单独成功；不要在 catch 中保存假成功日志；不要忘记全行失败和重试耗尽路径；不要让 Controller 自由指定 action/actor/target；不要把异常、文件或审核 note 拼进 summary；不要继承通用 Mapper 后声称不可修改；不要用 `INSERT IGNORE` 吞错；不要让 REVIEWER 被 `.anyRequest().authenticated()` 意外放行；不要修改 V1～V9 或预填测试数字。
- **回滚**：设计阶段只回退本设计文档和任务清单；实现阶段的 Java、测试、权限和文档可按 Day 5 文件范围回退，Day 4 风险/审核能力必须继续工作。V10 一旦进入共享或需保留数据的数据库不得修改/删除，只能新增 V11+ 补偿迁移；不得删除现有业务数据或数据卷掩盖问题。
- **提交建议**：`feat: add critical business audit logs`

### Week 5 Day 6：Redis 统计缓存、登录限流与上传限流

- **状态**：已完成
- **业务目标**：使用一个 Redis 实例缓存可重建的全局统计快照，并为登录与 CSV 上传增加原子固定窗口限流；MySQL 继续是业务真源，Redis 故障不得破坏导入、对账、风险、审核、审计或幂等正确性。
- **当前基线**：
  - Week 5 Day 5 已在提交 `f58ca6e` 完成；当前最新 Flyway 为 V10，共 14 张业务表，风险、审核、乐观锁和五类审计均已落地。
  - 进入本日时完整 `mvn clean test` 为 344/344；Day 6 完成后最终回归为 366/366。
  - 进入本日时仓库没有 Redis 依赖、Compose 服务、连接配置、`statistics`/`ratelimit` 模块或 429 错误码；这些能力均在本日按下列边界实现。
- **目标请求流**：

  ```text
  GET statistics
    → Redis 固定 key
    → hit：返回原快照
    → miss/Redis 故障：MySQL COUNT/GROUP BY → 可用时缓存 60 秒

  相关业务写事务成功提交
    → AFTER_COMMIT 删除统计 key
    → 删除失败只告警，TTL 最终纠正

  登录 / ADMIN 上传
    → Redis Lua 原子 INCR + 首次 PEXPIRE + PTTL
    → 阈值内进入现有流程
    → 超限返回 429 + Retry-After
    → Redis 故障 fail-open
  ```
- **范围边界**：
  - 只实现一个统计聚合接口、一个统计缓存 key、登录限流和 CSV 上传限流，以及必要的 Redis 基础设施、统一错误和测试。
  - 不新增或修改 Flyway 迁移；实现完成后仍是 V10 和 14 张业务表。
  - 不实现 Session、JWT 黑名单、验证码、账户锁定、分布式锁、Redisson、通用缓存平台、滑动窗口/令牌桶、网关限流、动态规则、前端或 Week 6 监控部署。
  - 不缓存进行中的任务详情、原始 CSV、审计原文或任意用户输入；Redis 不参与数据库最终幂等、审核并发或审计唯一性。
- **关键设计决定**：
  - 使用 Spring Data Redis 与 Boot 管理的 Lettuce；Compose 固定实际验证过的 Redis 8.x Alpine patch，不使用 `latest`，密码和超时全部外置。
  - 统计 key 为 `finguard:statistics:overview:v1`，显式 JSON DTO，TTL 60 秒；不使用 JDK 原生序列化或隐藏 key/故障边界的通用缓存注解。
  - `GET /api/statistics/overview` 统计 import jobs、reconciliation jobs/results、risk hits 和 review tasks，允许 ADMIN/REVIEWER；generatedAt 表示 MySQL 快照生成时间，缓存命中时不变化。
  - 业务事务内发布统计变化事件，`AFTER_COMMIT` 监听器执行幂等删除；回滚不删除，删除失败不回滚已提交业务。
  - 登录 key 为 IP 摘要 + 规范化用户名摘要，默认 5 次/300 秒；失败保留计数，JWT 成功签发后清除当前 key，只使用实际 remote address，不信任未配置代理的转发头。
  - 上传 key 为已验签 userId，默认 10 次/60 秒；必须先通过 ADMIN 授权，再在文件读取、哈希、解析和数据库访问前计数；匿名/REVIEWER 不消耗配额。
  - Lua 原子执行 INCR、首次 PEXPIRE 和 PTTL；第 limit+1 次返回统一 `429 RATE_LIMIT_EXCEEDED`，`Retry-After` 至少 1 秒且响应不泄漏计数、key 或账户存在性。
  - Redis 连接/命令故障时统计查 MySQL、限流 fail-open；配置错误、代码契约错误和数据库错误不能被 `catch (Exception)` 冒充 Redis 降级。
- **执行顺序**：
  1. 以 `docs/design/week5-day6-redis-cache-and-rate-limit-design.md` 锁定知识点、key、TTL、限流时机、失效事件、降级、文件和测试矩阵。
  2. 加入 Redis 依赖、Compose 服务、外置密码、短超时和类型安全配置，验证真实连接、认证、TTL 与健康状态。
  3. 实现显式 Redis JSON 组件和类路径 Lua 固定窗口算法，先用真实 Redis 测试原子计数、TTL、不续期和失败行为。
  4. 实现统计 Mapper/DTO/Service/Controller/RBAC，覆盖 miss、hit、TTL、损坏缓存、Redis 故障和 DB 异常边界。
  5. 在导入、对账、风险/审核生成与审核决策事务中发布变化事件，证明只在成功提交后失效，回滚和幂等短路不产生错误副作用。
  6. 接入登录与上传限流，保持既有 401 防枚举语义、上传 401/403 顺序、文件幂等和数据库约束不变。
  7. 完成聚焦测试、完整回归及真实 JWT/HTTP/MySQL/RabbitMQ/Redis 验收；停止/恢复 Redis 验证 fail-open 和恢复。
  8. 清理业务数据、8 个队列、`finguard:*` 验收 key、临时凭据和端口；执行敏感信息检查、Day 7/Week 6 范围审计和 `git diff --check` 后再提交。
- **任务**：
  - [x] 新增 Day 6 任务安排文档，讲清业务目标、请求流、知识点、设计选择、文件计划、测试矩阵、验收和回滚；本项不代表运行时能力已实现。
  - [x] 新增 `spring-boot-starter-data-redis`、固定版本 Redis Compose 服务、项目专属 volume、健康检查、外置密码和本地连接配置。
  - [x] 新增 Redis/统计/限流 `@ConfigurationProperties`，校验 TTL、阈值、窗口和超时均为合法正值。
  - [x] 实现受限 Redis 字符串/JSON 组件与固定 key 命名，确保没有 JDK 序列化、明文凭据或任意用户输入 key。
  - [x] 实现统计 MySQL 聚合、不可变响应、缓存旁路、60 秒 TTL、损坏缓存恢复和 Redis 读写失败降级。
  - [x] 实现 `GET /api/statistics/overview` 与 ADMIN/REVIEWER/匿名/无角色完整权限矩阵。
  - [x] 在导入受理/终态、对账受理/终态、风险/审核生成和审核决策成功提交后失效统计 key；事务回滚不得失效。
  - [x] 实现 Lua 固定窗口组件，覆盖首次 TTL、已有窗口不续期、并发原子计数、`PTTL=-1` 修复和 Retry-After 计算。
  - [x] 实现登录 5 次/300 秒限流：DTO 校验后、密码验证前计数，失败保留、成功清除，key 和响应不泄漏账户信息。
  - [x] 实现上传 10 次/60 秒限流：ADMIN 授权后、文件读取前计数，匿名/REVIEWER 不消耗配额，重复/无效文件消耗配额。
  - [x] 新增 `RateLimitExceededException`、`RATE_LIMIT_EXCEEDED` 和统一 `429 + Retry-After`，拒绝请求无数据库/RabbitMQ副作用。
  - [x] 覆盖缓存 miss/hit/TTL/提交后失效/回滚/损坏值、限流并发/窗口恢复/成功登录清除、RBAC 与 Redis 故障自动化测试。
  - [x] 运行聚焦测试和完整 `mvn clean test`，使用真实 Redis/JWT/HTTP/MySQL/RabbitMQ 验收并记录实际数字；完成清理、敏感信息检查、范围审计和 `git diff --check`。
- **关键文件**：
  - `docs/design/week5-day6-redis-cache-and-rate-limit-design.md`
  - `TASKS.md`
  - `pom.xml`
  - `docker-compose.yml`
  - `.env.example`
  - `src/main/resources/application.yml`
  - `src/main/resources/redis/fixed-window-rate-limit.lua`
  - `src/main/java/com/finguard/core/redis/`
  - `src/main/java/com/finguard/core/statistics/`
  - `src/main/java/com/finguard/core/ratelimit/`
  - `src/main/java/com/finguard/core/auth/controller/AuthController.java`
  - `src/main/java/com/finguard/core/importjob/controller/ImportJobController.java`
  - `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`
  - `src/main/java/com/finguard/core/common/exception/ErrorCode.java`
  - `src/main/java/com/finguard/core/common/exception/GlobalExceptionHandler.java`
  - `src/test/java/com/finguard/core/statistics/`
  - `src/test/java/com/finguard/core/ratelimit/`
- **验收标准**：
  - Redis 认证、固定版本、健康检查、短超时、显式 JSON/数字序列化和外置配置均可由代码与真实容器验证，仓库不含真实凭据。
  - 统计首次 miss 后缓存、第二次 hit 不重复查库、TTL 到期重建；业务成功提交后 key 删除，回滚不删除；Redis 故障时从 MySQL 返回正确结果。
  - 登录第 6 次、上传第 11 次返回 `429 + Retry-After`，窗口后恢复；成功登录清除当前窗口，失败登录继续计数；匿名/REVIEWER 上传不消耗配额。
  - Redis 暂停时登录/上传 fail-open，重复上传、MQ 红投和审核并发仍由 MySQL/RabbitMQ 既有不变量保护，无重复业务副作用。
  - V1～V10 checksum 不变且无 V11；完整测试不少于历史 344 项，0 failures、0 errors、0 skipped；真实验收、数据/key/消息/端口清理、敏感信息检查、范围审计和 `git diff --check` 全部通过。
- **验收结论**：Redis 8.2.8 Alpine、MySQL 8.4.10 与 RabbitMQ 4.3.4 均健康，Java 17 应用 `/actuator/health` 为 `UP`。专项测试 31/31、最终完整 `mvn clean test` 366/366，均为零失败、零错误、零跳过。真实 ADMIN/REVIEWER JWT 验证统计权限、60 秒 JSON 缓存、命中保持 `generatedAt`、导入提交后失效与 MySQL 重建；真实导入消息到达终态。登录第 6 次、上传第 11 次均返回 `429 + Retry-After`；暂停 Redis 后统计仍从 MySQL 返回 200，登录和授权语义保持，恢复后缓存重建。五条聚合 SQL 已在真实 MySQL 执行 `EXPLAIN`，未发现新增 V11/索引的依据。验收用户/任务、8 个队列、全部 `finguard:*` key、临时凭据和 18080 端口均已清理；Flyway 仍为 V10、业务表仍为 14 张。
- **学习重点**：缓存旁路、TTL、最终一致性、`AFTER_COMMIT` 失效、Redis 字符串/JSON 序列化、Lua 原子性、固定窗口算法、摘要 key、`429/Retry-After`、fail-open 与可用性/安全取舍。
- **常见错误预防**：不要让 Redis 成为业务真源；不要在事务提交前删缓存；不要用非原子的 INCR+EXPIRE；不要把用户名/密码/JWT/CSV 拼进 key；不要信任未配置代理的转发头；不要在授权前消耗上传配额；不要用全量异常捕获掩盖数据库或代码错误；不要预填测试数字或宣称未经测量的性能提升。
- **回滚**：可按 Day 6 文件范围回退 Redis 依赖、容器、配置、统计/限流代码和测试，核心 MySQL/RabbitMQ 流程必须继续运行。不得修改/删除 V8～V10 或风险、审核、审计数据；只清理本项目明确命名且确认可重建的 `finguard:*` key 和 Redis volume，不删除 MySQL/RabbitMQ 数据卷。
- **提交建议**：`feat: add redis statistics cache and rate limits`

### Week 5 Day 7：综合验收、清理与周复盘

- **状态**：未开始
- **业务目标**：从可重建环境证明 Week 5 的风险识别、异常审核、并发控制、审计、缓存和限流形成完整且可解释的闭环，并生成面试可复核证据。
- **范围边界**：只验收 Week 5 已实现能力并修复真实缺陷；不新增 Week 6 的 Docker 镜像、CI/CD、Linux 部署、Micrometer、Prometheus/Grafana、压测或安全报告功能。
- **任务**：
  - [ ] 新增 `docs/review/week5-review.md`，按 Day 记录设计承诺、实际实现、提交、偏差、限制和 Week 6 边界。
  - [ ] 从确认可清理的环境验证 MySQL、RabbitMQ、Redis 健康，Flyway 与 Redis key/TTL/限流配置可自动重建。
  - [ ] 运行风险、审核、审计、Redis、认证、导入、对账聚焦测试和完整 `mvn clean test`，记录实际数字。
  - [ ] 使用真实 ADMIN/REVIEWER JWT + HTTP 跑通上传、异步导入、异步对账、风险命中、审核查询、确认/忽略和审计查询。
  - [ ] 并发提交同一审核任务，验证一个成功、一个稳定 `409`，最终只有一个决策和一条对应审计记录。
  - [ ] 验证统计缓存命中与失效、登录/上传 `429` 和窗口恢复；暂停 Redis 验证降级，不通过手工改库伪造成功。
  - [ ] 复核重复 MQ、重复风险评估、重复审核、Outbox 重试和 Redis 故障均不会产生重复业务副作用。
  - [ ] 清理验收数据、Redis key、RabbitMQ 消息、临时凭据、进程和端口；更新 README、TASKS 和 Git 里程碑索引。
  - [ ] 执行范围审计、敏感信息检查、`git diff --check` 和提交前复核。
- **关键文件**：
  - `TASKS.md`
  - `README.md`
  - `docs/review/week5-review.md`
  - `src/test/java/com/finguard/core/risk/`
  - `src/test/java/com/finguard/core/review/`
  - `src/test/java/com/finguard/core/audit/`
  - `src/test/java/com/finguard/core/statistics/`
  - `src/test/java/com/finguard/core/ratelimit/`
- **验收**：三类风险规则、异常审核、乐观锁、五类审计、统计缓存和两类限流均有自动化与真实 HTTP/MySQL/Redis/RabbitMQ 证据；完整回归零失败/错误/跳过；数据、消息、缓存、凭据和端口清理完成。
- **提交建议**：`docs: complete week 5 acceptance review`

## 8. 后续路线

后续 Day 的详细任务在进入当天时，按本文统一模板补充。候选顺序如下，实际边界以当天设计评审为准。

| 阶段 | 候选交付 |
|---|---|
| Week 3 | 导入表结构、同步 CSV 上传与解析、逐行校验、SHA-256 去重、批量入库、同步版自动对账、周验收 |
| Week 4 | Day 1～Day 7 已完成；异步导入/对账、Outbox、重试/DLQ、综合验收与周复盘均已收口 |
| Week 5 | Day 1～Day 7 已规划；风险、审核、乐观锁、审计、Redis 缓存/限流和周验收按顺序推进 |
| Week 6 | Docker 镜像、GitHub Actions、Linux 部署、Micrometer、Prometheus/Grafana、压测、安全测试、故障演练和最终文档 |

## 9. Git 里程碑索引

| 里程碑 | 提交 |
|---|---|
| 工程初始化 | `f740ceb` |
| 阶段 0 验收 | `b422d7d` |
| Week 1 Day 2 数据模型 | `297abd0` |
| Week 1 Day 3 MySQL/Flyway | `3b3b33b` |
| Week 1 Day 4 账户 CRUD | `d118d98` |
| Week 1 Day 5 交易 CRUD | `43d3724` |
| Week 1 Day 6 分页与错误处理 | `81a466f` |
| 学习计划调整 | `b0516b3` |
| Week 1 复盘与验收 | `83ae373`、`3c3043e` |
| Week 2 Day 1 认证设计 | `0afaaba` |
| Week 2 Day 2 认证持久层 | `2666a00` |
| Week 2 Day 3 登录/JWT | `7d14297` |
| Week 2 Day 4 JWT 入站认证 | `33ca50c` |
| Week 2 Day 5 RBAC | `573069b` |
| Week 2 Day 6 事务/索引 | `dbe799d` |
| Week 2 Day 7 综合验收 | `7febe0d` |
| Week 3 Day 1 CSV 契约 | `42bf721` |
| Week 3 Day 2 导入持久层 | `8c24cb1` |
| Week 3 Day 3 CSV 解析 | `471a36d` |
| Week 3 Day 4 行校验 | `2b52b6a` |
| Week 3 Day 5 同步导入 | `ce5e020` |
| Week 3 Day 6 同步对账 | `20626dd` |
| Week 3 Day 7 综合验收 | `e404682` |
| Week 4 Day 1 异步消息契约设计 | `996c013` |
| Week 4 Day 2 RabbitMQ 基础设施 | `7f77980` |
| Week 4 Day 3 Outbox 可靠发布 | `bfd9a25` |
| Week 4 Day 4 异步导入消费者 | `e6c6edf` |
| Week 4 Day 5 导入消费幂等与崩溃恢复 | `be4779b` |
| Week 4 Day 6 异步对账、有限重试与死信隔离 | `0015610` |
| Week 4 Day 7 综合验收 | `c7a1156` |
| Week 5 Day 1 风险、审核、Redis 与审计契约设计 | `71b432d` |
| Week 5 Day 2 风险命中持久层与规则契约骨架 | `0cbb71a` |
| Week 5 Day 3 三条风险规则与审核任务生成 | `5128247` |
| Week 5 Day 4 审核接口与乐观锁并发控制 | `286391f` |
| Week 5 Day 5 关键业务操作审计日志 | `f58ca6e` |
| Week 5 Day 6 Redis 统计缓存与固定窗口限流 | 本次提交 |
