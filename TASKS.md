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
| Week 3 Day 4 | 下一步 | 实现逐字段规范化、业务校验、账户查询和重复交易判断 |
| Week 3 Day 5～Day 7 | 待规划 | 同步编排与批量入库、自动对账和周验收 |
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
