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
| Week 4 | 待规划 | RabbitMQ 异步化、可靠投递、消费幂等、重试和死信 |
| Week 5 | 待规划 | Redis、风险规则、异常审核、乐观锁和审计 |
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

## 7. 后续路线

后续 Day 的详细任务在进入当天时，按本文统一模板补充。候选顺序如下，实际边界以当天设计评审为准。

| 阶段 | 候选交付 |
|---|---|
| Week 3 | 导入表结构、同步 CSV 上传与解析、逐行校验、SHA-256 去重、批量入库、同步版自动对账、周验收 |
| Week 4 | RabbitMQ 异步导入与对账、Publisher Confirm、手动 ACK、消费幂等、重试和死信队列 |
| Week 5 | Redis 缓存与限流、风险规则、异常审核、乐观锁、审计日志和周验收 |
| Week 6 | Docker 镜像、GitHub Actions、Linux 部署、Micrometer、Prometheus/Grafana、压测、安全测试、故障演练和最终文档 |

## 8. Git 里程碑索引

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
| Week 3 Day 7 综合验收 | 待提交 |
