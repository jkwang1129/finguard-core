# FinGuard Core 任务清单

> 本文保留已完成里程碑的验收记录，并以复选框作为任务状态的唯一依据。
> “当前执行入口”只指向下一个里程碑，不重复复制详细任务。

## 当前进度

| 阶段 | 状态 | 当前结论 |
|---|---|---|
| 阶段 0 | 已完成 | 工程、Git、Spring Boot 与健康检查基线已建立 |
| 第 1 周 | 已完成 | 账户、交易 CRUD、分页、统一错误处理和周验收已完成 |
| 第 2 周 Day 1～Day 5 | 已完成 | 认证持久层、登录、JWT、无状态过滤链和账户/交易 RBAC 已完成 |
| 第 2 周 Day 6 | 已完成 | 事务边界、索引与 `EXPLAIN` |
| 第 2 周 Day 7 | 已完成 | Week 2 综合验收与复盘 |
| 第 3 周 Day 1 | 下一步 | CSV 导入需求、文件契约、幂等键和失败策略设计 |

## 阶段 0：工程基线（已完成）

- [x] 检查当前目录和已有文件
- [x] 确认 JDK 17 可用
- [x] 确认 Maven 可用
- [x] 确认 Git 可用
- [x] 确认 Docker 与 Docker Compose 可用（Docker Desktop 4.82.0、Engine 29.6.1、Compose v5.3.0，使用 WSL2 后端）
- [x] 初始化 Git 仓库
- [x] 创建最小 Spring Boot 项目
- [x] 验证应用能够启动
- [x] 验证 `/actuator/health` 返回 `UP`
- [x] 运行并通过第一条测试
- [x] 建立 `README.md` 和 `TASKS.md`
- [x] 完成第一次 Git 提交

## 第 1 周任务清单（已完成）

目标：完成工程基线、数据库设计，以及账户与交易的最小 CRUD；学习 Maven、Spring MVC、分层、MySQL 和 REST。

### Day 1：环境与最小骨架（已完成）

- [x] 完成阶段 0 全部验收项
- [x] 理解 Maven 项目结构、依赖管理和常用生命周期
- [x] 理解 Spring Boot 启动流程与 Actuator 健康检查
- [x] 保留一次清晰的初始 Git 提交

### Day 2：需求与数据模型（已完成）

- [x] 明确账户、交易的最小业务规则和验收标准
- [x] 绘制第一版 ER 图
- [x] 设计账户表、交易表及必要唯一约束
- [x] 完成设计评审，未提前生成业务表
- [x] 提交 Day 2 设计文档
- 设计文档：[`docs/design/day2-requirements-and-data-model.md`](docs/design/day2-requirements-and-data-model.md)

### Day 3：MySQL 与迁移基线（已完成）

- [x] 用 Docker Compose 启动 MySQL
- [x] 配置数据源和 MyBatis-Plus
- [x] 引入 Flyway 并创建首个最小迁移
- [x] 验证应用能连接数据库

### Day 4：账户 CRUD（已完成）

- [x] 讲清 Controller、Service、Mapper 以及 DTO、Entity 的职责边界
- [x] 建立账户模块骨架和创建、查询、改名、状态切换、软删除接口
- [x] 完成账户 Service 核心业务逻辑并逐项解释实现过程
- [x] 完成输入标准化、参数校验、重复编号预检查和数据库唯一约束兜底
- [x] 完成 15 个 Service 单元测试和 6 个 Controller 参数校验测试
- [x] 运行 `mvn clean test`，26 个测试全部通过且无跳过项
- [x] 在真实 MySQL 上走通创建、查询、改名、禁用和软删除流程
- [x] 验证活动账户不能删除、软删除账户默认查不到且编号不能复用
- [x] 将统一错误响应以及 404/409 状态映射按计划留到 Day 6

### Day 5：交易 CRUD（已完成）

- [x] 明确金额使用 `BigDecimal` 的精度与舍入约束
- [x] 讲清交易时间、业务流水号、`MANUAL` 来源和数据库唯一约束
- [x] 建立交易 Entity、枚举、DTO、VO、Mapper 和异常骨架
- [x] 完成交易 Service 核心业务逻辑并逐项解释实现过程
- [x] 完成创建、查询、修改和删除的最小闭环
- [x] 完成 28 个 Service 单元测试和 12 个 Controller 参数校验测试
- [x] 运行 `mvn clean test`，66 个测试全部通过且无跳过项
- [x] 在真实 MySQL 上走通创建、查询、修改、禁用账户查询和软删除流程
- [x] 验证禁用账户不能创建或删除交易、重复删除幂等成功
- [x] 验证软删除交易默认查不到、原流水号不能复用且失败请求未写入数据库

### Day 6：查询与错误处理（已完成）

- [x] 完成账户和交易的基础分页、稳定排序与组合条件查询
- [x] 建立统一错误响应与全局异常处理，完成 `400`、`404`、`409`、`500` 映射
- [x] 补充 Controller、Service 和真实 MySQL 集成测试
- [x] 运行 `mvn clean test`，86 个测试全部通过且无跳过项
- [x] 真实启动应用并验证分页查询、参数错误、资源不存在、重复数据和非法状态操作
- [x] 精确清理 HTTP 验收数据并确认 8080 端口释放

### Day 7：周验收与复盘（已完成）

- [x] 从空环境实际运行本周流程
- [x] 整理学习笔记、常见错误和面试问题
- 复盘文档：[`docs/review/week1-review.md`](docs/review/week1-review.md)
- [x] 检查 Git 历史是否连续且每次提交可解释
- [x] 只在本周目标稳定后进入第 2 周

## 第 2 周任务清单（进行中）

目标：完成最小认证与授权闭环，用 Spring Security、JWT 和 RBAC 保护现有账户、交易接口；结合真实查询学习事务边界、索引和 `EXPLAIN`。Week 1 已完成的分页与 Flyway 基线不重复开发，本周只做增量迁移和安全集成。

本周范围：

- 登录方式只做用户名、密码登录和 JWT Access Token，不做刷新令牌、OAuth 2.0、第三方登录和前端页面
- 密码只保存 BCrypt 哈希；JWT 密钥必须来自环境变量，不写入仓库、日志或接口响应
- `ADMIN` 可以读写账户和交易；`REVIEWER` 本周只能查询账户和交易，为后续异常审核预留角色
- `/actuator/health` 和 `/api/auth/login` 匿名可访问；其余业务接口默认必须认证
- 认证失败返回 `401`，已认证但权限不足返回 `403`，并沿用统一错误响应结构
- 不修改已经执行的 `V1`，认证表和索引通过新的 Flyway 迁移创建

### Day 1：认证需求、权限矩阵与技术方案（已完成）

目标：先锁定“谁可以通过什么方式访问哪些接口”，形成 Day 2～Day 5 的唯一设计依据。当天只做知识学习、方案设计、文档和基线验证，不修改 `pom.xml`、Java 代码、配置文件或数据库迁移。

当日交付物：`docs/design/week2-day1-authentication-design.md`

#### 任务 1：理解认证授权全流程

- [x] 从 FinGuard 业务说明为什么账户、交易、导入和审核接口不能继续匿名访问
- [x] 讲清认证与授权、JWT 与 Session、RBAC 与普通角色判断的区别
- [x] 讲清 Spring Security 中 Filter Chain、Authentication、SecurityContext 和 GrantedAuthority 的职责
- [x] 讲清 `400`、`401`、`403` 的使用边界，重点区分“没有合法身份”和“身份合法但权限不足”
- [x] 画出从登录到访问受保护接口的请求流：
  `用户名密码 → 身份校验 → 签发 JWT → Bearer Token → 校验 JWT → 建立身份 → 检查权限 → Controller`

#### 任务 2：锁定登录接口和 JWT 契约

- [x] 定义 `POST /api/auth/login` 的请求字段、校验规则、成功响应和失败响应
- [x] 登录成功统一返回 `accessToken`、`tokenType=Bearer` 和 `expiresInSeconds`
- [x] JWT 只保留 `iss`、`sub`、`username`、`roles`、`iat`、`exp` 等必要 Claims，不保存密码或敏感业务数据
- [x] 当前版本使用两小时有效的 Access Token，不实现 Refresh Token、Token 黑名单和主动登出
- [x] 选择适合单体应用的 `HS256`，密钥使用至少 256 bit 的环境变量，不写入仓库
- [x] 定义登录参数错误、用户名或密码错误、用户禁用时的 HTTP 状态和统一错误码

#### 任务 3：建立角色权限矩阵

- [x] 匿名用户只允许访问 `POST /api/auth/login` 和 `GET /actuator/health`
- [x] `ADMIN` 可以查询、创建、修改、切换状态和删除账户
- [x] `ADMIN` 可以查询、创建、修改和删除人工交易
- [x] `REVIEWER` 只能查询账户列表、账户详情、交易列表和交易详情
- [x] `REVIEWER` 本周不能写账户或交易；后续只为审核任务开放确认、忽略权限
- [x] 未在权限矩阵中声明的新业务接口默认拒绝匿名访问
- [x] 将现有每个 Controller 路由和 HTTP Method 都放入矩阵，避免只按 URL 前缀做模糊授权

#### 任务 4：设计最小认证数据模型

- [x] 设计 `users`：用户 ID、规范化用户名、密码哈希、状态、创建时间、更新时间
- [x] 用户名统一 `trim + lowercase`，限制为 3～64 位，并建立不可复用的唯一约束
- [x] 密码字段保存 BCrypt 哈希而不是明文，长度为算法前缀和后续升级预留空间
- [x] 用户状态只保留 `ACTIVE`、`DISABLED`，本阶段不做注册、找回密码和软删除
- [x] 设计 `roles`：角色 ID、唯一角色编码和必要时间字段，只初始化 `ADMIN`、`REVIEWER`
- [x] 设计 `user_roles`：用户与角色外键、联合主键，禁止同一角色重复绑定
- [x] 明确外键删除策略、唯一约束、必要索引和 Day 2 的 Flyway V2 建表顺序
- [x] 明确 Flyway 只初始化角色，不写入明文密码或通用默认用户

#### 任务 5：锁定实现边界和测试方案

- [x] 决定使用 Spring Security 原生 JWT/Resource Server 能力，避免手写不完整的鉴权框架
- [x] 密码使用 BCrypt；登录失败统一提示，不能暴露用户是否存在
- [x] 本地验收用户通过默认关闭的受控初始化方式创建，凭据来自环境变量
- [x] 列出 Day 2～Day 5 预计新增的 `auth` 模块文件，但 Day 1 不创建代码骨架
- [x] 设计后续测试场景：正确登录、错误密码、禁用用户、无 Token、过期 Token、篡改 Token、角色越权
- [x] 确认本周不加入 Redis 登录限流；只记录为后续安全增强，避免跨越 Week 2 边界

#### 任务 6：形成文档并完成 Day 1 验收

- [x] 完成认证请求流、登录契约、JWT 决策、权限矩阵、ER 图、约束和测试清单
- [x] 对照现有账户、交易 Controller，确认权限矩阵没有漏掉任何路由
- [x] 运行 `mvn clean test`，确认 Week 1 全部测试继续通过且无跳过项
- [x] 运行 `git diff --check`，确认文档格式正确且改动只属于 Day 1
- [x] 完成设计评审；未评审通过前不进入 Flyway V2 和 Security 代码

Day 1 建议提交信息：`docs: design authentication and authorization`

### Day 2：认证表、Flyway V2 与持久层（已完成）

- [x] 新增 Flyway `V2`，创建 `users`、`roles`、`user_roles` 表并初始化 `ADMIN`、`REVIEWER` 角色
- [x] 为用户名唯一性、用户角色关联和外键关系建立必要约束与索引
- [x] 建立认证模块的 Entity、枚举、Mapper 和最小查询对象
- [x] 实现按规范化用户名加载用户及其角色的查询
- [x] 准备认证集成测试夹具，测试用户不写入正式 Flyway 迁移
- [x] 增加迁移、唯一约束、外键和角色查询的真实 MySQL 集成测试
- [x] 运行完整测试并确认 Flyway 从空库可一次迁移到最新版本

Day 2 建议提交信息：`feat: add authentication persistence layer`

### Day 3：密码校验、登录接口与 JWT 签发（已完成）

目标：在 Day 2 认证持久层之上完成“用户名密码 → 身份校验 → 签发 JWT”的最小登录闭环。当天签发的 Token 必须能够被测试代码真实验签，但暂不把 Token 接入账户、交易接口，也不实现入站 JWT 过滤链和 RBAC；这些分别属于 Day 4、Day 5。

当日交付物：

- `POST /api/auth/login` 登录接口；
- BCrypt 密码校验与统一登录失败响应；
- 使用 HS256 签发、两小时有效的 JWT；
- 默认关闭、凭据外置、可幂等执行的本地验收用户初始化器；
- 登录、JWT 和初始化器的自动化测试；
- 真实 MySQL、HTTP 登录和 Token 验签记录。

#### 任务 1：先讲清登录闭环中的关键知识

- [x] 讲清密码哈希与加密的区别，以及 BCrypt 的随机盐、成本因子和 72 字节输入边界
- [x] 讲清 `UserDetailsService`、`UserDetails`、`PasswordEncoder`、`AuthenticationManager` 和认证提供者之间的调用关系
- [x] 讲清 JWT 是“已签名的身份声明”而不是加密数据，也不是权限规则本身
- [x] 讲清 `iss`、`sub`、`username`、`roles`、`iat`、`exp` 各自的用途和两小时有效期的取舍
- [x] 讲清为什么“用户名不存在”“密码错误”“用户被禁用”对外必须返回完全相同的 `401`
- [x] 画出当天请求流：
  `LoginRequest → AuthController → AuthService → AuthenticationManager → DatabaseUserDetailsService → UserMapper → BCrypt 校验 → JwtTokenIssuer → LoginResponse`

#### 任务 2：引入最小依赖并完成安全基础配置

- [x] 只引入 Day 3 所需的 Spring Security Core、BCrypt 和 JWT/Jose 能力，不提前配置 Resource Server 过滤链
- [x] 配置 `PasswordEncoder`，统一使用 BCrypt；业务代码不得自行比较明文密码或手写哈希逻辑
- [x] 建立 `JwtProperties`，从 `JWT_SECRET_BASE64` 读取 Base64 密钥，并固定 `issuer=finguard-core`、`expiresInSeconds=7200`
- [x] 应用启动时校验密钥存在、Base64 可解码且解码后不少于 32 字节；失败信息只能说明配置项错误，不能输出密钥
- [x] 为 JWT 签发注入可控 `Clock`，避免测试依赖真实当前时间
- [x] 为自动化测试提供明确标记为仅测试使用的固定配置，不能把真实密钥写入仓库

涉及文件：

```text
pom.xml
src/main/resources/application.yml
src/main/java/com/finguard/core/auth/config/JwtProperties.java
src/main/java/com/finguard/core/auth/config/JwtConfiguration.java
src/main/java/com/finguard/core/auth/config/PasswordConfiguration.java
src/test/resources/application.properties
src/test/java/com/finguard/core/auth/config/AuthSecurityConfigurationTest.java
```

#### 任务 3：定义登录接口契约与参数边界

- [x] 创建 `LoginRequest`，用户名必填，并提供供业务层复用的 `trim + lowercase` 规范化工具
- [x] 密码必填、区分大小写、不执行 `trim`，并按 UTF-8 字节数校验为 1～72 字节
- [x] 创建 `LoginResponse`，只返回 `accessToken`、`tokenType=Bearer` 和 `expiresInSeconds`
- [x] 使用测试专用契约 Controller 验证空用户名、空密码、超长密码和 malformed JSON 继续沿用统一 `400` 错误结构
- [x] 确认 DTO/VO 的 `toString` 不会包含密码或完整 Token
- [x] 将生产 `AuthController` 延至任务 4，与真实 `AuthService` 一起接入，避免注册无法工作的假接口

涉及文件：

```text
src/main/java/com/finguard/core/auth/dto/LoginRequest.java
src/main/java/com/finguard/core/auth/vo/LoginResponse.java
src/main/java/com/finguard/core/auth/validation/AuthInputNormalizer.java
src/main/java/com/finguard/core/auth/validation/NormalizedUsernameLength.java
src/main/java/com/finguard/core/auth/validation/NormalizedUsernameLengthValidator.java
src/main/java/com/finguard/core/auth/validation/Utf8ByteLength.java
src/main/java/com/finguard/core/auth/validation/Utf8ByteLengthValidator.java
src/test/java/com/finguard/core/auth/dto/LoginContractTest.java
```

#### 任务 4：实现用户加载、密码校验与统一认证失败

- [x] 创建 `AuthController`，通过 `@Valid` 接收 `LoginRequest`、调用真实 `AuthService` 并返回 `LoginResponse`
- [x] 实现 `DatabaseUserDetailsService`，复用 Day 2 的 `findAuthUserByNormalizedUsername` 查询并装配角色
- [x] 将数据库中的 `ACTIVE`、`DISABLED` 状态转换为 Spring Security 能理解的账户状态
- [x] 配置认证流程，让 BCrypt 比较请求密码与数据库哈希，禁止把数据库哈希传出认证模块
- [x] 实现 `AuthService`：规范化用户名、发起认证、读取认证成功后的用户 ID 与角色，再调用 JWT 签发器
- [x] 用户不存在、密码错误和用户禁用统一转换为同一个认证领域异常
- [x] 在 `ErrorCode` 和 `GlobalExceptionHandler` 中增加 `401 INVALID_CREDENTIALS`，固定消息为 `Invalid username or password`
- [x] 记录失败时只保留必要的非敏感上下文，不记录密码、哈希、JWT 密钥或完整 Token

任务 4 只定义 `TokenIssuer` 签发端口；任务 5 完成真实 `JwtTokenIssuer` 后，已经移除临时登录开关并正式启用生产登录接口。

涉及文件：

```text
src/main/java/com/finguard/core/auth/controller/AuthController.java
src/main/java/com/finguard/core/auth/config/AuthAuthenticationConfiguration.java
src/main/java/com/finguard/core/auth/model/AuthenticatedUser.java
src/main/java/com/finguard/core/auth/security/AuthPrincipal.java
src/main/java/com/finguard/core/auth/security/DatabaseUserDetailsService.java
src/main/java/com/finguard/core/auth/security/TokenIssuer.java
src/main/java/com/finguard/core/auth/service/AuthService.java
src/main/java/com/finguard/core/auth/service/impl/AuthServiceImpl.java
src/main/java/com/finguard/core/auth/exception/InvalidCredentialsException.java
src/main/java/com/finguard/core/common/exception/ErrorCode.java
src/main/java/com/finguard/core/common/exception/GlobalExceptionHandler.java
src/test/java/com/finguard/core/auth/AuthLoginIntegrationTest.java
src/test/java/com/finguard/core/auth/config/AuthAuthenticationConfigurationTest.java
src/test/java/com/finguard/core/auth/controller/AuthControllerTest.java
src/test/java/com/finguard/core/auth/security/DatabaseUserDetailsServiceTest.java
src/test/java/com/finguard/core/auth/service/AuthServiceImplTest.java
```

#### 任务 5：使用框架原生能力签发 JWT

- [x] 实现 `JwtTokenIssuer`，使用框架提供的 JWT 编码器和 HS256，不手写 Base64 拼接或 HMAC 签名
- [x] `iss` 固定为 `finguard-core`，`sub` 使用用户 ID 字符串
- [x] 写入规范化 `username` 和排序稳定、无重复的 `roles`
- [x] 使用注入的 `Clock` 生成 `iat`，并令 `exp = iat + 7200 秒`
- [x] Token 中不得写入密码、密码哈希、账户数据或其他敏感业务字段
- [x] 签发测试必须对 Token 真实解码和验签，不能只断言字符串非空

涉及文件：

```text
src/main/java/com/finguard/core/auth/security/JwtTokenIssuer.java
src/main/java/com/finguard/core/auth/config/JwtConfiguration.java
src/test/java/com/finguard/core/auth/security/JwtTokenIssuerTest.java
src/test/java/com/finguard/core/auth/AuthLoginIntegrationTest.java
```

#### 任务 6：实现受控的本地验收用户初始化器

- [x] 建立 `AuthBootstrapProperties`，总开关 `FINGUARD_AUTH_BOOTSTRAP_ENABLED` 默认必须为 `false`
- [x] 开启时从环境变量读取 `ADMIN`、`REVIEWER` 的用户名和密码；缺项时快速失败，但错误信息不得包含凭据
- [x] 用户名沿用 `trim + lowercase`；验收密码除 BCrypt 的 1～72 字节限制外，至少 12 个字符
- [x] 密码入库前先转为 BCrypt 哈希，数据库中绝不能出现明文密码
- [x] 在一个事务中完成用户创建和角色绑定，防止只创建用户但未分配角色
- [x] 重复启动必须幂等：已存在的同名用户不重复创建、不重复绑定角色，也不静默覆盖旧密码
- [x] 默认关闭时不得查询、创建或修改任何认证数据

涉及文件：

```text
src/main/java/com/finguard/core/auth/bootstrap/AuthBootstrapProperties.java
src/main/java/com/finguard/core/auth/bootstrap/AuthBootstrapRunner.java
src/main/java/com/finguard/core/auth/mapper/RoleMapper.java
src/test/java/com/finguard/core/auth/bootstrap/AuthBootstrapIntegrationTest.java
src/test/java/com/finguard/core/auth/bootstrap/AuthBootstrapRunnerTest.java
```

#### 任务 7：建立分层自动化测试

- [x] `AuthService` 单元测试覆盖成功登录、用户名规范化、用户不存在、错误密码和禁用用户
- [x] 验证三种认证失败得到完全相同的 `401` 错误码和消息
- [x] `AuthController` 测试覆盖成功响应、字段校验和 malformed JSON
- [x] JWT 测试覆盖签发方、用户 ID、用户名、角色、签发时间、过期时间和真实签名验证
- [x] JWT 配置测试覆盖缺少密钥、非法 Base64 和解码后不足 256 bit
- [x] 初始化器集成测试覆盖默认关闭、成功创建、BCrypt 入库、正确角色绑定、重复执行幂等和配置缺失
- [x] 运行 `mvn clean test`，要求全部测试通过且无跳过项

#### 任务 8：完成真实 MySQL 与 HTTP 验收

- [x] 先运行 `docker compose ps`，确认 `finguard-mysql` 为 `healthy`
- [x] 使用临时环境变量开启初始化器并创建验收 `ADMIN`、`REVIEWER`，不把凭据写入 `.env`、命令历史或文档
- [x] 启动应用并轮询 `/actuator/health`，确认返回 `UP`
- [x] 用正确管理员凭据请求 `POST /api/auth/login`，确认返回 `200`、`Bearer` 和 `7200`
- [x] 对返回的 JWT 真实验签并检查 Claims，不在验收输出中打印完整 Token
- [x] 分别验证错误密码、不存在用户和禁用用户均返回同样的 `401 INVALID_CREDENTIALS`
- [x] 查询 MySQL，确认只保存 BCrypt 哈希且没有明文密码
- [x] 清理验收用户与角色绑定，确认残留计数为零，关闭临时初始化开关并释放 8080 端口
- [x] 运行 `git diff --check` 并检查改动只属于 Day 3

#### Day 3 验收标准

- [x] 正确凭据能够获得一个可真实验签、两小时有效的 JWT
- [x] JWT 只包含约定的必要 Claims，不包含任何密码或敏感业务数据
- [x] 用户不存在、密码错误和用户禁用不会泄露账号状态
- [x] 本地初始化器默认关闭、凭据外置、密码哈希入库且重复执行幂等
- [x] 原有账户、交易、分页、异常处理和数据库测试没有回归
- [x] 未提前实现 JWT 入站校验、无状态过滤链、账户/交易接口保护或 RBAC
- [x] 自动化测试、真实 HTTP/MySQL 验收和工作区格式检查全部通过

Day 3 建议提交信息：`feat: implement login and JWT issuance`

### Day 4：无状态 Security 过滤链与 JWT 校验（已完成）

- [x] 讲清 Security Filter Chain、SecurityContext 和 Bearer Token 请求流
- [x] 配置无状态会话，不使用服务端 Session 保存登录状态
- [x] 放行健康检查和登录接口，其他业务接口默认要求合法 JWT
- [x] 校验 JWT 签名、格式和过期时间，并将角色转换为 Spring Security 权限
- [x] 为未登录、Token 格式错误、签名错误和 Token 过期实现统一 `401` 响应
- [x] 增加完整 Spring 上下文安全集成测试，不用仅能覆盖 MVC 的伪安全测试代替

### Day 5：RBAC 与现有接口授权（已完成）

- [x] 按权限矩阵保护账户和交易的查询、创建、修改、状态切换与删除接口
- [x] 验证 `ADMIN` 可以完成账户、交易完整操作
- [x] 验证 `REVIEWER` 可以查询但不能写入账户或交易
- [x] 为权限不足实现统一 `403` 响应，并明确它与 `401` 的区别
- [x] 覆盖匿名、`ADMIN`、`REVIEWER` 的接口权限矩阵测试
- [x] 在真实 MySQL 上用真实登录 Token 走通登录、查询、写入和越权拒绝流程

Day 5 验收记录：

- 在 `SecurityConfiguration` 集中声明权限矩阵：账户和交易的列表、详情查询允许 `ADMIN`、`REVIEWER`，七类写接口只允许 `ADMIN`
- 新增 `RestAccessDeniedHandler` 和 `ACCESS_DENIED`，已认证但权限不足统一返回 `403`、`Insufficient permissions`，且不返回 `WWW-Authenticate`
- `RbacAuthorizationIntegrationTest` 使用完整 Spring 上下文覆盖 ADMIN 全操作、REVIEWER 四类读取与七类写入拒绝、匿名 11 个路由的 `401`、无角色 Token 的 `403`
- 修复 JWT 篡改测试偶尔修改 Base64URL 非有效位的问题，改为修改签名段首字符，确保测试真实改变签名字节
- `mvn clean test` 共 155 项测试全部通过，无失败、错误或跳过
- 真实验收确认健康状态 `UP`、ADMIN/REVIEWER 登录成功、ADMIN 读写成功、REVIEWER 只读和七类越权写入全部正确拒绝
- REVIEWER 越权请求未改变数据库；验收用户密码为 BCrypt；交易、账户、用户角色和用户清理计数均为零，8080 端口已释放

Day 5 建议提交信息：`feat: enforce RBAC for business endpoints`

### Day 6：事务边界、索引与 EXPLAIN（已完成）

- [x] 梳理认证和现有账户、交易 Service 的事务边界，区分只读事务与写事务
- [x] 不为演示事务硬加业务接口；通过测试专用的用户与角色多表写入微实验验证 `@Transactional` 异常时整体回滚
- [x] 学习 Spring 事务代理、自调用失效、受检异常回滚和事务范围过大的常见问题
- [x] 使用 `EXPLAIN` 检查用户名查询、用户角色查询和交易分页查询的执行计划
- [x] 说明联合索引最左前缀、回表、覆盖索引，以及 `%keyword%` 无法正常利用 B-Tree 前缀的原因
- [x] 只根据真实查询和执行计划新增必要索引，不为“看起来可能有用”的字段堆索引
- [x] 记录事务回滚与索引分析结果，并运行完整测试

Day 6 验收记录：

- `DatabaseUserDetailsService` 使用短只读事务完成用户与角色读取；账户、交易和认证初始化的原有读写事务边界经审计后保持
- `TransactionBoundaryIntegrationTest` 通过测试专用 Spring 代理 Bean 验证用户与角色绑定在运行时异常时整体回滚，聚焦认证与事务测试 9 项全部通过
- 使用 10 个临时账户、2,000 条交易和认证测试数据执行真实 `EXPLAIN`；用户名唯一索引、用户角色复合主键和按账户交易索引均继续保留
- 默认交易分页修改前为全表扫描并出现 `Using filesort`；新增 `V3__add_transaction_pagination_index.sql` 后使用 `(deleted, transaction_time DESC, id DESC)`，不再文件排序
- `mvn clean test` 共 157 项测试全部通过，无失败、错误或跳过项
- 独立临时空库成功执行 Flyway `V1` 到 `V3`，数据库、认证、索引和事务回滚 11 项测试全部通过，临时库已删除
- 真实应用健康状态为 `UP`，匿名交易请求返回 `401`；账户、交易、用户和用户角色残留均为零，固定角色为 2，8080 端口已释放
- 分析文档：[`docs/analysis/week2-day6-transaction-and-index-analysis.md`](docs/analysis/week2-day6-transaction-and-index-analysis.md)

Day 6 建议提交信息：`perf: verify transactions and optimize query indexes`

### Day 7：Week 2 综合验收与复盘（已完成）

目标：不再增加业务功能，通过空库迁移、自动化回归、真实 HTTP/MySQL 安全验收、事务与索引复核，证明 Week 2 的“登录 → JWT 校验 → RBAC 授权”闭环可以从干净环境稳定重建、运行和解释。

当天边界：

- 只验收 Week 2 已完成的认证、授权、事务和索引能力，不提前实现 Week 3 CSV 导入
- 不为展示效果新增接口、角色、刷新令牌、Redis、OAuth 2.0 或前端页面
- 如果验收发现真实缺陷，只做最小修复并重新执行受影响测试和完整验收；不顺手重构无关代码
- JWT 密钥和验收账号密码继续使用临时环境变量，不写入仓库、文档、日志或命令输出

#### 任务 1：锁定验收基线和环境

- [x] 检查 `git status --short` 和 Week 2 Git 历史，确认 Day 1～Day 6 的提交边界连续、工作区没有无关改动
- [x] 运行 `docker compose ps`，确认 `finguard-mysql` 为 `healthy`
- [x] 确认验收使用 Java 17、当前 Maven Wrapper/项目 Maven 配置和 `127.0.0.1:3306`
- [x] 记录验收开始前账户、交易、用户、用户角色和固定角色数量，避免把旧数据误认为本次结果

#### 任务 2：验证空库迁移和数据库约束

- [x] 创建独立临时空库，从 Flyway `V1` 执行到最新迁移，禁止修改已经应用的迁移文件
- [x] 确认 `accounts`、`transactions`、`users`、`roles`、`user_roles` 表以及外键、唯一约束、状态约束和必要索引存在
- [x] 确认只初始化固定的 `ADMIN`、`REVIEWER` 两个角色，不创建默认明文账号
- [x] 运行数据库、认证持久层和事务回滚相关集成测试，确认约束拒绝非法数据且异常时多表写入整体回滚
- [x] 删除临时空库，确认没有遗留验收数据库

#### 任务 3：运行完整自动化回归

- [x] 运行 `mvn clean test`，要求全部测试通过，且失败、错误、跳过项均为零
- [x] 确认账户 CRUD、交易 CRUD、分页筛选、统一异常、登录、JWT、过滤链、RBAC、事务和数据库测试均未回归
- [x] 记录实际测试总数和结果，不把某一天的历史测试数量写成固定验收标准

#### 任务 4：完成真实 HTTP/MySQL 认证授权验收

- [x] 使用临时环境变量开启本地初始化器，创建一个 `ADMIN` 和一个 `REVIEWER` 验收用户；确认密码只以 BCrypt 哈希入库
- [x] 启动真实应用并轮询 `/actuator/health`，确认返回 `UP`
- [x] 用正确凭据登录两个用户，确认返回 `200`、`Bearer`、`expiresInSeconds=7200`，并对 JWT 真实验签和检查必要 Claims
- [x] 使用 `ADMIN` Token 走通账户和交易的创建、查询、修改、状态切换和删除流程
- [x] 使用 `REVIEWER` Token 走通账户、交易查询，并确认所有写请求返回 `403 ACCESS_DENIED` 且数据库没有变化
- [x] 使用匿名请求访问受保护接口，确认返回统一 `401` 且包含预期 `WWW-Authenticate` 响应头

#### 任务 5：覆盖失败与攻击路径

- [x] 验证错误密码、不存在用户和禁用用户返回完全相同的 `401 INVALID_CREDENTIALS`，不泄露账号状态
- [x] 验证无 Token、错误 Bearer 格式、篡改签名和过期 Token 均返回统一 `401`
- [x] 验证已认证但角色不足返回 `403`，并明确记录它与未认证 `401` 的区别
- [x] 确认响应、日志和复盘材料中都不包含密码、BCrypt 哈希、JWT 密钥或完整 Token

#### 任务 6：复核事务与索引证据

- [x] 重新运行事务边界微实验，确认运行时异常会同时回滚用户创建和角色绑定
- [x] 使用具有代表性的数据量执行关键查询的 `EXPLAIN`：规范化用户名查询、用户角色查询、默认交易分页和按账户交易分页
- [x] 确认查询使用预期唯一索引、复合主键和交易分页索引；默认交易分页不再出现全表扫描加 `Using filesort`
- [x] 只有执行计划证明存在问题时才允许增加索引；Day 7 不为猜测中的查询堆索引

#### 任务 7：清理现场并形成复盘产物

- [x] 删除验收账户、交易、用户和用户角色，确认业务与验收数据残留为零、固定角色仍为 2
- [x] 关闭临时初始化开关、清除临时凭据和密钥环境变量，停止应用并确认 8080 端口已释放
- [x] 运行 `git diff --check`，检查最终改动只属于 Day 7 验收与复盘
- [x] 创建 `docs/review/week2-review.md`，记录验收证据、请求流、权限矩阵、事务与索引结论、常见错误和修复
- [x] 在复盘文档中整理 Week 2 核心知识点、可口述的面试问题、Git 提交历史、仍然存在的限制和 Week 3 进入条件

#### Day 7 验收标准

- [x] 干净空库能够完整执行 Flyway `V1` 到最新迁移，数据库结构、约束、索引和固定角色正确
- [x] 完整自动化测试全部通过，真实应用健康状态为 `UP`
- [x] 真实 JWT 登录、`ADMIN` 完整操作、`REVIEWER` 只读、匿名拒绝和所有失败路径均符合契约
- [x] 事务回滚和关键 `EXPLAIN` 结论有可复查证据，验收数据、临时凭据和 8080 端口全部清理
- [x] `docs/review/week2-review.md` 和 Git 历史足以让本人完整讲清 Week 2 的设计、实现、测试与取舍
- [x] 只有以上项目全部通过后，才把当前执行入口切换到 Week 3 Day 1 CSV 导入设计

Day 7 验收记录：

- 独立临时空库成功执行 Flyway `V1` 到 `V3`；5 张核心表、2 个认证外键、2 个固定角色和 3 列交易分页索引均正确
- 空库数据库、认证 Mapper 与事务测试 14 项通过；完整 `mvn clean test` 共 157 项通过，失败、错误和跳过均为 0
- 真实应用健康状态为 `UP`；ADMIN 账户和交易完整操作、REVIEWER 四类读取与七类写入拒绝、匿名 401 全部通过
- 错误密码、不存在用户和禁用用户得到相同 401；无 Token、Bearer 格式错误、篡改 Token、过期 Token 与越权 403 均符合契约
- 两个临时用户密码只以 BCrypt 哈希入库；日志未发现密码、密钥、完整 Token 或 BCrypt 哈希
- 事务回滚测试通过；10 个账户、2,000 条交易下的用户名、角色、默认分页和按账户分页 `EXPLAIN` 均使用预期索引
- 验收结束后账户、交易、用户、用户角色和临时数据库残留均为 0，固定角色为 2，8080 端口已释放
- 复盘文档：[`docs/review/week2-review.md`](docs/review/week2-review.md)

Day 7 建议提交信息：`docs: complete week 2 acceptance review`

## 当前执行入口

- 当前里程碑：Week 3 Day 1——CSV 导入需求与契约设计（待开始）
- 前置状态：Week 2 Day 1～Day 7 已完成，认证授权闭环、事务、索引和综合验收均已通过
- 实现边界：先设计 CSV 文件格式、字段映射、幂等键、错误行策略和验收标准，不提前实现 RabbitMQ 或对账
- 任务明细：在开始 Week 3 Day 1 时补充并锁定，不在 Week 2 复盘提交中提前实现
