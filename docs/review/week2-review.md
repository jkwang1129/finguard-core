# Week 2 综合验收与复盘

## 1. 验收结论

Week 2 最终验收通过。

FinGuard Core 已经完成最小但完整的认证授权闭环：

```text
用户名密码
  → 数据库加载用户与角色
  → BCrypt 校验
  → 签发两小时 JWT
  → Bearer Token 入站校验
  → 建立 SecurityContext
  → RBAC 权限判断
  → 账户/交易 Controller
  → Service 事务
  → Mapper 与 MySQL
```

本周不是只做到“能够登录”，而是证明了以下能力可以一起工作：

- 认证表能够通过 Flyway 从空库重建；
- 密码只以 BCrypt 哈希保存；
- JWT 能够真实签名、验签、校验过期时间并恢复用户角色；
- 匿名用户、Token 非法、Token 过期和权限不足具有不同且稳定的 HTTP 契约；
- `ADMIN` 可以读写账户和交易，`REVIEWER` 只能读取；
- 认证、账户和交易的事务边界明确；
- 关键查询使用有证据支持的索引；
- 自动化测试、真实 HTTP/MySQL 验收和数据清理全部通过。

Day 7 没有增加 Week 3 功能，没有实现 CSV 导入、刷新令牌、Redis、OAuth 2.0 或前端页面。

## 2. Week 2 交付历史

| Day | 提交 | 交付内容 |
|---|---|---|
| Day 1 | `0afaaba` | 认证授权设计、登录契约、权限矩阵、ER 图和测试清单 |
| Day 2 | `2666a00` | `users`、`roles`、`user_roles`、Mapper 和 Flyway `V2` |
| Day 3 | `7d14297` | BCrypt 登录、统一认证失败、JWT 签发和本地初始化器 |
| Day 4 | `33ca50c` | 无状态 Security Filter Chain、JWT 入站校验和统一 401 |
| Day 5 | `573069b` | 账户/交易 RBAC、统一 403 和真实权限矩阵验收 |
| Day 6 | `dbe799d` | 事务边界微实验、真实 `EXPLAIN` 和交易分页索引 `V3` |
| Day 7 | 当前提交 | 空库、回归、安全、事务、索引、清理和综合复盘 |

这些提交保持“一天一个可解释里程碑”的边界，没有把后续功能混进较早的提交。

## 3. 认证请求流

### 3.1 登录成功

```text
POST /api/auth/login
  → LoginRequest 参数校验
  → 用户名 trim + lowercase
  → AuthService
  → AuthenticationManager
  → DaoAuthenticationProvider
  → DatabaseUserDetailsService
  → UserMapper 查询用户
  → RoleMapper 查询角色
  → PasswordEncoder.matches 执行 BCrypt 校验
  → JwtTokenIssuer
  → LoginResponse
```

业务代码不读取或比较明文数据库密码。数据库只向认证模块提供 BCrypt 哈希，密码比较交给 Spring Security 的 `PasswordEncoder`。

### 3.2 登录失败

以下三种情况对外完全相同：

- 用户不存在；
- 密码错误；
- 用户被禁用。

统一响应：

```text
401 INVALID_CREDENTIALS
Invalid username or password
```

这样调用方不能通过响应差异枚举有效用户名或推断用户状态。

### 3.3 JWT 契约

JWT 使用 HS256，有效期为 7200 秒，签发方固定为 `finguard-core`。Token 只保存必要 Claims：

- `iss`：签发方；
- `sub`：用户 ID；
- `username`：规范化用户名；
- `roles`：去重并稳定排序后的角色；
- `iat`：签发时间；
- `exp`：过期时间。

Token 不包含密码、BCrypt 哈希、账户、交易或其他敏感业务数据。JWT 是带签名的身份声明，不是加密数据，也不负责独立决定权限。

## 4. 受保护请求流

```text
HTTP 请求
  → SecurityFilterChain
  → 读取 Authorization: Bearer <token>
  → JwtDecoder 校验格式、HS256 签名、issuer 和 exp
  → JwtRoleConverter 将 roles 转成 ROLE_ADMIN / ROLE_REVIEWER
  → SecurityContext 保存当前认证身份
  → 路由权限矩阵判断
  → Controller
  → Service
  → Mapper
  → MySQL
```

服务端使用 `SessionCreationPolicy.STATELESS`，不会在 Session 中保存登录状态。每次受保护请求都必须携带并重新校验 JWT。

## 5. RBAC 权限矩阵

### 5.1 读取接口

`ADMIN` 和 `REVIEWER` 均可访问：

| 方法 | 路由 | 含义 |
|---|---|---|
| GET | `/api/accounts` | 账户分页查询 |
| GET | `/api/accounts/{id}` | 账户详情 |
| GET | `/api/transactions` | 交易分页查询 |
| GET | `/api/transactions/{id}` | 交易详情 |

### 5.2 写入接口

只有 `ADMIN` 可以访问：

| 方法 | 路由 | 含义 |
|---|---|---|
| POST | `/api/accounts` | 创建账户 |
| PATCH | `/api/accounts/{id}/name` | 修改账户名 |
| PATCH | `/api/accounts/{id}/status` | 修改账户状态 |
| DELETE | `/api/accounts/{id}` | 删除账户 |
| POST | `/api/transactions` | 创建交易 |
| PUT | `/api/transactions/{id}` | 修改交易 |
| DELETE | `/api/transactions/{id}` | 删除交易 |

### 5.3 401 与 403

- `401`：请求没有建立合法身份，例如无 Token、Token 格式错误、签名错误或 Token 过期；
- `403`：请求已经通过认证，但当前角色无权执行该操作，例如 `REVIEWER` 写入账户。

真实验收确认 REVIEWER 的七类写请求全部返回 `403 ACCESS_DENIED`，且拒绝前后数据库计数没有变化。

## 6. 数据库与 Flyway

### 6.1 迁移顺序

独立临时空库成功执行：

```text
V1 账户和交易
  → V2 用户、角色和用户角色
  → V3 默认交易分页索引
```

最终 Flyway 版本为 3。五张核心表全部存在：

- `accounts`
- `transactions`
- `users`
- `roles`
- `user_roles`

`V2` 只初始化 `ADMIN`、`REVIEWER` 两个固定角色，不创建默认账号，更不会写入明文密码。

### 6.2 约束

空库测试验证了：

- 用户名唯一；
- 角色代码只允许 `ADMIN`、`REVIEWER`；
- 用户状态只允许 `ACTIVE`、`DISABLED`；
- 用户与角色绑定使用复合主键，不能重复绑定；
- 用户角色外键使用 `RESTRICT`；
- 不存在的用户或角色不能建立绑定；
- 交易金额、方向、来源和逻辑删除值受数据库约束保护。

已经应用的迁移文件没有被修改；所有变化继续使用增量迁移。

## 7. 事务边界

事务主要放在 Service 层，因为 Service 方法代表完整业务操作。

### 7.1 写事务

典型写事务包括：

- 创建、修改、删除账户；
- 创建、修改、删除交易；
- 初始化用户并绑定角色。

它们通常包含读取、业务校验、写入和回读，必须作为一个整体成功或失败。

### 7.2 只读事务

账户查询、交易查询和 `DatabaseUserDetailsService` 使用只读事务。用户加载与角色加载组成一个短读取边界，但 BCrypt 计算和 JWT 签发不占用数据库事务。

### 7.3 回滚微实验

测试专用 Spring Bean 在同一事务内：

1. 插入用户；
2. 插入用户角色绑定；
3. 主动抛出 `IllegalStateException`；
4. 事务退出后重新查询。

最终用户和角色绑定计数都为 0，证明运行时异常跨过 Spring AOP 代理后，两次写入会整体回滚。

## 8. 索引与 EXPLAIN

Day 7 使用 10 个临时账户、2000 条交易、1800 条有效交易和一个双角色用户重新分析执行计划。

| 查询 | type | key | 估算行数 | Extra |
|---|---|---|---:|---|
| 规范化用户名查询 | `const` | `uk_users_username` | 1 | 无额外扫描 |
| 用户角色查询 | `index/eq_ref` | 角色唯一索引、`PRIMARY`、用户名唯一索引 | 很小 | `Using index` |
| 默认交易分页 | `ref` | `idx_transactions_deleted_time_id` | 1800 | 无 `Using filesort` |
| 按账户交易分页 | `ref` | `idx_transactions_account_time` | 200 | `Using where; Backward index scan` |

结论：

- 用户名唯一索引已经满足登录等值查询；
- `user_roles(user_id, role_id)` 的最左列支持按用户查角色，不需要重复的 `user_id` 单列索引；
- `(deleted, transaction_time DESC, id DESC)` 支持默认分页排序；
- `(account_id, transaction_time)` 继续服务按账户分页；
- 没有因为“字段可能会查”而继续堆索引。

## 9. Day 7 验收证据

### 9.1 环境基线

- MySQL 容器：`healthy`
- MySQL：8.4.10
- Maven：3.9.16
- Maven 使用的 Java：17.0.10
- 应用数据库地址：`127.0.0.1:3306`
- 开始前数据：账户 0、交易 0、用户 0、用户角色 0、固定角色 2

### 9.2 空库与聚焦测试

- 空库成功迁移到 `V3`
- 核心表：5
- 固定角色：`ADMIN, REVIEWER`
- 认证外键：2
- 默认分页索引列：3
- 数据库、认证 Mapper 和事务聚焦测试：14/14 通过
- 临时数据库残留：0

### 9.3 完整自动化测试

```text
Tests run: 157
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

### 9.4 真实 HTTP/MySQL

- `/actuator/health` 返回 `UP`
- ADMIN、REVIEWER 登录均返回 `200`、`Bearer`、`7200`
- 两个 JWT 均重新计算 HS256 签名并检查 Claims
- ADMIN 账户和交易完整操作通过
- REVIEWER 四类读取通过
- REVIEWER 七类写入全部为 `403`，且未改变数据
- 匿名读取返回 `401 AUTHENTICATION_REQUIRED`
- 错误密码、不存在用户、禁用用户返回相同 `401`
- Bearer 格式错误、篡改 Token、过期 Token 返回 `401 INVALID_TOKEN`
- 两个临时密码均只以 BCrypt 哈希入库
- 应用日志未发现密码、密钥、完整 Token 或 BCrypt 哈希

### 9.5 最终清理

```text
accounts=0
transactions=0
users=0
user_roles=0
roles=2
temporary_databases=0
port_8080=free
```

真实删除接口使用逻辑删除。为了同时验证删除契约并满足“验收数据原始表计数为 0”，Day 7 在接口返回成功后，只针对本次随机 ID 的逻辑删除记录执行了受控硬清理。

## 10. Day 7 验收工具问题

Day 7 没有发现需要修改生产代码的缺陷，但验收工具出现过以下问题：

- 禁止把临时凭据直接拼入命令文本，改为运行时内存生成；
- Windows PowerShell 不支持静态 `RandomNumberGenerator.Fill`，改用 `Create().GetBytes()`；
- `Invoke-WebRequest` 对非 2xx 响应体读取不稳定，改用统一的 .NET `HttpClient`；
- `StrictMode` 下空管道结果没有可靠的 `.Count`，改为显式数组；
- PowerShell 向 MySQL 传递动态 SQL 时必须把 SQL 先构造成单个参数；
- 账户和交易删除为逻辑删除，验收清理不能把“接口返回 204”等同于“原始表行数为 0”。

这些问题都发生在临时验收脚本中。每次失败路径均执行清理，最终脚本没有进入提交。

## 11. 常见错误

- 把 JWT 当成加密数据，在 Token 中放入密码或业务敏感信息；
- 自己拼接 Base64 和 HMAC，绕开框架提供的 JWT 能力；
- 把“用户不存在”和“密码错误”返回成不同消息，泄露账号状态；
- 只验证 Token 非空，不真实验签；
- 把所有登录用户都当成同一权限，未区分认证和授权；
- 把 401 与 403 混为一谈；
- 使用 Session 保存登录状态，却声称系统是无状态 JWT；
- 在同一个类中自调用 `@Transactional` 方法，误以为代理会生效；
- 捕获运行时异常后正常返回，导致事务提交；
- 在小表上看到全表扫描就盲目增加索引；
- 只看 `possible_keys`，不看实际 `key` 和 `Extra`；
- 修改已经执行的 Flyway 迁移；
- 清理逻辑删除数据时只查询默认视图，误以为原始表没有残留。

## 12. 面试问题与简答

### 认证和授权有什么区别？

认证回答“你是谁”，例如用户名密码校验和 JWT 验签；授权回答“你能做什么”，例如 REVIEWER 可以读取但不能创建账户。

### 为什么密码使用 BCrypt？

BCrypt 是专门用于密码存储的慢哈希算法，包含随机盐和可调整成本。它比通用快速哈希更能抵抗离线暴力破解。

### 为什么三种登录失败返回相同消息？

避免攻击者根据响应判断用户名是否存在、密码是否正确或账号是否禁用。

### JWT 为什么不是加密数据？

JWT 的 Header 和 Payload 可以被客户端解码。签名只能证明内容未被篡改和签发方可信，不能隐藏 Payload。

### 无状态 JWT 的代价是什么？

服务端不保存 Session，横向扩展简单；但 Token 在过期前通常难以即时撤销，需要较短有效期、密钥轮换或后续撤销机制。

### 为什么 401 和 403 必须分开？

401 表示尚未建立合法身份；403 表示身份合法但权限不足。分开后客户端行为、日志分析和安全语义都更清楚。

### Spring Security 如何把 JWT 角色用于授权？

JwtDecoder 验证 Token，JwtRoleConverter 把 `roles` 转成 `ROLE_ADMIN` 等 Authority，SecurityFilterChain 再根据路由规则判断是否允许访问。

### 为什么事务放在 Service？

Service 表达完整业务操作，能够把读取、校验和多次写入放在同一原子边界中。Controller 和 Mapper 都不适合作为主要业务事务边界。

### 为什么登录不包一个大事务？

只有用户和角色读取需要短事务。BCrypt 校验和 JWT 签发是计算操作，把它们放进事务会无理由延长连接占用。

### 为什么默认分页索引以低区分度的 deleted 开头？

主要目的不是只靠 `deleted` 大幅过滤，而是让数据库在未删除记录范围内直接按时间和 ID 倒序读取前 20 行，避免文件排序。

### 为什么账户删除后还要验收硬清理？

业务接口使用逻辑删除以保留历史，但 Day 7 的测试数据不应污染后续里程碑。先验证逻辑删除接口，再仅对随机验收 ID 做硬清理，可以同时满足业务契约和干净环境要求。

## 13. 已知限制

- 当前只有 Access Token，没有刷新令牌和服务端撤销列表；
- HS256 使用共享密钥，适合当前单体范围，但后续需要密钥轮换策略；
- 本地用户初始化器只用于受控验收，默认关闭；
- 没有登录限流、验证码、MFA 或安全告警；
- 没有前端登录页面；
- Flyway 11.7.2 对 MySQL 8.4 输出“高于已测试的 8.1”警告；当前空库迁移、约束和完整测试均通过，但升级依赖时仍需复核兼容性；
- CSV 文件安全、导入幂等和批处理事务属于 Week 3。

## 14. Week 3 进入条件

以下条件已经满足：

- 空库迁移成功；
- 157 项完整测试通过；
- 真实登录、JWT、RBAC 和失败路径通过；
- 事务回滚与索引执行计划有证据；
- 验收数据和端口清理完成；
- Week 2 提交边界和复盘材料可解释。

下一里程碑可以进入 Week 3 Day 1：CSV 导入需求、文件契约、幂等键和失败策略设计。本复盘不提前实现 CSV 功能。
