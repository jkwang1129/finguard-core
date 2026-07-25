# FinGuard Core Week 1 验收与复盘

## 1. 复盘结论

Week 1 的项目工程目标已经达到：FinGuard Core 可以从空数据库启动，Flyway 能创建账户与交易表，账户和人工交易 CRUD、分页条件查询、统一错误处理均可运行，并通过自动化测试与真实 HTTP 回归。

截至 2026-07-25：

- MySQL 8.4.10 容器健康检查为 `healthy`；
- Flyway 当前版本为 V1；
- `mvn clean test` 通过 86 个测试，0 失败、0 错误、0 跳过；
- 账户、交易、分页、参数错误、资源不存在、数据重复、非法状态和软删除已通过真实 HTTP 验收；
- 已能独立说明 Controller、DTO、Service、Mapper、MySQL、Response 和全局异常处理的完整请求链路；
- Git 历史已经完成连续性与提交边界检查，算法与基础知识综合检查得分 91/100；Week 1 最终验收通过。

本周没有实现 Security、JWT、RBAC、CSV 导入、自动对账、RabbitMQ、Redis、审核或审计。这些属于后续周次，不应描述为当前能力。

---

## 2. 本周实际交付

### Day 1：工程与运行基线

- Java 17 与 Maven 工程；
- Spring Boot 3.5.16 最小应用；
- Spring MVC 与 Actuator；
- `/actuator/health` 健康检查；
- 第一条应用启动集成测试；
- README、任务清单和连续 Git 历史。

### Day 2：需求与数据模型

- 明确账户与交易的最小业务规则；
- 设计 `accounts` 与 `transactions` 表；
- 建立一对多关系；
- 确定金额、状态、来源、软删除和唯一约束；
- 形成 `docs/design/day2-requirements-and-data-model.md`。

### Day 3：数据库基线

- Docker Compose 启动 MySQL 8.4.10；
- Spring Boot DataSource 与 HikariCP；
- MyBatis-Plus；
- Flyway V1 数据库迁移；
- `DatabaseBaselineIntegrationTest` 验证数据库连接、Flyway、MyBatis 和业务表。

### Day 4：账户 CRUD

- 创建账户；
- 按 ID 查询账户；
- 修改账户名称；
- 切换账户状态；
- 账户软删除；
- 输入标准化；
- 重复编号预检查和数据库唯一约束兜底；
- 活动账户和有交易历史账户的删除保护。

### Day 5：人工交易 CRUD

- 创建 `MANUAL` 交易；
- 按 ID 查询交易；
- 修改交易的方向、金额、时间和描述；
- 交易软删除；
- 重复删除幂等；
- `BigDecimal` 金额校验；
- 交易时间与业务流水号校验；
- 禁用账户的交易操作保护。

### Day 6：查询与错误处理

- 账户分页与组合条件查询；
- 交易分页与组合条件查询；
- 稳定排序；
- 统一错误响应；
- `400`、`404`、`409`、`500` 状态映射；
- Controller、Service 和真实 MySQL 集成测试。

### Day 7 已完成部分

- 删除旧 MySQL 数据卷，从空环境重新初始化；
- 验证 Flyway V1 和两张业务表；
- 重新运行 86 个自动化测试；
- 真实启动应用并手动完成账户、交易、分页、异常和软删除回归；
- 清理验收数据并释放 8080 端口；
- 完成请求链路口述验收；
- 整理本复盘文档；
- 审计 Git 历史，确认提交线性连续且每个提交均有明确的里程碑边界；
- 完成算法、Java、MySQL 和 HTTP 基础综合检查，得分 91/100。

---

## 3. 当前技术基线

| 领域 | 当前选择 |
|---|---|
| Java | Java 17 |
| 构建 | Maven 3.9+ |
| Web | Spring Boot 3.5.16、Spring MVC |
| 参数校验 | Jakarta Validation |
| 数据访问 | MyBatis-Plus 3.5.17 |
| 数据库 | MySQL 8.4.10 |
| 连接池 | HikariCP |
| 数据库迁移 | Flyway |
| 测试 | JUnit 5、Mockito、MockMvc、Spring Boot Test |
| 本地依赖 | Docker Compose |
| 健康检查 | Spring Boot Actuator |

当前结构是单个 Spring Boot 应用内的模块化单体，不是微服务。账户、交易与通用错误处理按业务包拆分，保持一次部署和一次数据库事务边界，适合六周实习项目范围。

---

## 4. 完整请求链路

```text
客户端
→ Tomcat
→ Spring MVC DispatcherServlet
→ Jackson 将 JSON 转换为 DTO
→ Jakarta Validation 基础参数校验
→ Controller
→ Service 业务规则与事务
→ Mapper / MyBatis-Plus
→ MySQL 最终数据约束
→ Entity
→ Response
→ Jackson 序列化 JSON
→ HTTP 响应
```

各层职责：

| 层 | 主要职责 | 不应承担 |
|---|---|---|
| Controller | HTTP 路径、参数接收、调用 Service、状态码 | 查重、状态机、SQL |
| DTO | 接收请求字段、基础格式校验 | 数据库映射、复杂业务决策 |
| Service | 输入标准化、业务规则、事务、流程编排 | HTTP 传输细节 |
| Entity | Java 与数据库表映射 | 直接作为公共 API 返回 |
| Mapper | 生成或执行数据库操作、结果映射 | 决定业务是否允许执行 |
| MySQL | 主键、外键、唯一和 CHECK 约束 | 生成友好的业务文案 |
| Response | 返回允许暴露的数据 | 暴露 `deleted` 等内部字段 |
| GlobalExceptionHandler | 统一错误状态与响应结构 | 实现账户或交易业务逻辑 |

成功链路以创建交易为例：

```text
POST /api/transactions
→ CreateTransactionRequest
→ TransactionController
→ TransactionService
→ TransactionMapper
→ transactions 表
→ TransactionResponse
→ HTTP 201
```

异常链路：

```text
参数、业务或数据库异常
→ GlobalExceptionHandler
→ ApiErrorResponse
→ HTTP 400 / 404 / 409 / 500
```

---

## 5. 核心业务规则

### 5.1 账户

- 账户编号会执行 `trim` 和大写标准化；
- 编号长度为 3～64，只允许大写字母、数字、下划线和连字符；
- 账户编号全局唯一，软删除后不能复用；
- 新账户默认币种为 `CNY`；
- 新账户默认状态为 `ACTIVE`；
- 只有 `DISABLED` 账户可以删除；
- 有任何交易历史的账户不能删除，包括只有软删除交易的账户；
- 普通查询不返回软删除账户。

### 5.2 交易

- 当前公开创建接口只能创建 `MANUAL` 交易；
- 客户端不能自行指定 `CSV_IMPORT`；
- 金额使用 Java `BigDecimal` 和 MySQL `DECIMAL(19,2)`；
- 金额必须大于 0，最多 17 位整数和 2 位小数；
- 交易时间不能超过当前业务时间五分钟；
- 只有 `ACTIVE` 账户可以创建新交易；
- 只有 `MANUAL` 交易可以修改或删除；
- 所属账户必须为 `ACTIVE` 才能删除交易；
- 重复删除已经软删除的交易仍返回成功；
- 普通查询不返回软删除交易；
- 软删除后的交易流水号不能复用。

### 5.3 交易唯一性

交易业务唯一键是：

```text
(account_id, source, external_transaction_no)
```

同一账户和流水号可以分别存在一条 `MANUAL` 和一条 `CSV_IMPORT` 数据，为后续对账保留空间；同一来源下不能重复。

Service 预查重用于提供清晰业务错误，数据库唯一约束用于处理并发下的最终正确性：

```text
Service 预查重
→ 友好提示

MySQL UNIQUE
→ 最终保证
```

---

## 6. 分页与排序

账户查询支持：

- `page`；
- `size`；
- `status`；
- `accountType`；
- `keyword`。

交易查询支持：

- `page`；
- `size`；
- `accountId`；
- `direction`；
- `source`；
- `externalTransactionNo`；
- `startTime`；
- `endTime`。

默认 `page=1`、`size=20`，最大 `size=100`。

账户按 `id DESC` 排序；交易按 `transaction_time DESC, id DESC` 排序。显式稳定排序用于避免翻页时记录顺序不确定、重复或遗漏。

---

## 7. 统一错误处理

统一错误响应包含：

```text
timestamp
status
code
message
path
fieldErrors
```

主要映射：

| 场景 | HTTP | 错误码 |
|---|---:|---|
| DTO 或方法参数校验失败 | 400 | `VALIDATION_FAILED` |
| 请求体格式或枚举错误 | 400 | `INVALID_REQUEST` |
| 账户不存在 | 404 | `ACCOUNT_NOT_FOUND` |
| 交易不存在 | 404 | `TRANSACTION_NOT_FOUND` |
| 账户编号重复 | 409 | `DUPLICATE_ACCOUNT_NO` |
| 交易业务键重复 | 409 | `DUPLICATE_TRANSACTION` |
| 非法账户操作 | 409 | `INVALID_ACCOUNT_OPERATION` |
| 非法交易操作 | 409 | `INVALID_TRANSACTION_OPERATION` |
| 未识别异常 | 500 | `INTERNAL_SERVER_ERROR` |

未知异常会在服务端记录完整堆栈，但客户端只返回通用消息，避免泄露数据库细节、SQL、文件路径或内部实现。

---

## 8. 验收证据

### 8.1 自动化测试

2026-07-25 执行：

```powershell
mvn clean test
```

结果：

```text
Tests run: 86
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

测试分布：

| 测试类 | 数量 |
|---|---:|
| `AccountControllerTest` | 14 |
| `AccountServiceImplTest` | 15 |
| `DatabaseBaselineIntegrationTest` | 4 |
| `GlobalExceptionHandlerIntegrationTest` | 3 |
| `HealthEndpointIntegrationTest` | 1 |
| `PaginationQueryIntegrationTest` | 2 |
| `TransactionControllerTest` | 18 |
| `TransactionServiceImplTest` | 29 |
| 合计 | 86 |

测试输出中的模拟未知异常堆栈是 `500` 安全响应测试的一部分；应以最终的失败数、错误数和 `BUILD SUCCESS` 判断测试结果。

### 8.2 空环境验收

实际执行：

```powershell
docker compose down -v
docker compose up -d
docker compose ps
mvn clean test
```

确认：

- 旧的 `finguard-core_mysql_data` 已删除；
- 新数据卷成功创建；
- MySQL 最终达到 `healthy`；
- Flyway 当前版本为 V1；
- `accounts` 与 `transactions` 表存在；
- 完整测试在新数据库上通过。

### 8.3 真实 HTTP 验收

| 场景 | 实际结果 |
|---|---:|
| Actuator 健康检查 | 200 |
| 创建账户 | 201 |
| 查询、改名、状态切换 | 200 |
| 删除合法账户 | 204 |
| 创建人工交易 | 201 |
| 查询和修改交易 | 200 |
| 删除及重复删除交易 | 204 |
| 非法分页参数 | 400 |
| 不存在的账户或交易 | 404 |
| 重复账户和交易 | 409 |
| 禁用账户创建交易 | 409 |
| 有历史交易的账户删除 | 409 |
| 账户和交易分页 | 200 |

验收结束后已停止应用、释放 8080、删除验收数据并重建干净数据库基线。

### 8.4 知识验收

已能够独立说明：

- Tomcat、DispatcherServlet、Controller、DTO、Service、Mapper、MySQL 和 Response 的请求链路；
- 基础参数校验由 DTO 与 Jakarta Validation 负责；
- 业务状态检查由 Service 负责；
- 最终唯一性由 MySQL UNIQUE 约束负责；
- Service 预查重与数据库约束不能互相替代。

算法与基础知识综合检查结果：

| 检查项 | 得分 |
|---|---:|
| HashSet 流水号查重 | 14/15 |
| 二分查找左边界 | 15/15 |
| BigDecimal 金额语义 | 17/20 |
| Java 集合与对象相等 | 14/15 |
| MySQL 并发、联合索引与软删除 | 19/20 |
| HTTP 请求链路与状态码 | 12/15 |
| **总分** | **91/100** |

需要保留的修正结论：

- 普通 `HashSet` 不保证返回顺序；要求按首次重复顺序输出时使用 `LinkedHashSet`，或在返回前显式排序；
- 严格限制金额最多两位小数时，应先检查 `scale()`，再使用 `setScale(2, RoundingMode.UNNECESSARY)`；弃用的 `BigDecimal.ROUND_UNNECESSARY` 不再使用；
- `Object` 默认相等语义是对象身份，不应简单描述为比较内存地址；
- FinGuard 按 REST 约定返回：创建成功 `201`、参数错误 `400`、资源不存在 `404`、重复冲突 `409`、未预期异常 `500`，不使用 `200 + 业务错误码` 替代错误状态。

---

## 9. 本周典型问题与处理

### 9.1 软删除过滤了历史数据

问题：普通 MyBatis-Plus 查询会自动过滤 `deleted=1`，但账户删除规则必须检查全部交易历史。

处理：为历史检查使用不带逻辑删除过滤的自定义 Mapper 查询。

结论：面向当前业务的查询可以过滤软删除，历史完整性检查不能过滤。

### 9.2 Service 查重不能替代数据库唯一约束

问题：两个并发请求可能都在预查重阶段看到“数据不存在”。

处理：保留 Service 预查重，并使用数据库唯一约束兜底；捕获 `DuplicateKeyException` 后转换为业务异常。

结论：Service 改善用户体验，数据库保证最终正确性。

### 9.3 删除操作需要明确幂等语义

问题：交易第一次软删除后，普通查询看不到记录，第二次删除可能被误判为不存在。

处理：删除时使用包含软删除记录的查询；如果已经删除，直接成功返回。

结论：幂等不是“什么都不检查”，而是重复请求保持相同业务结果且不产生新副作用。

### 9.4 PowerShell 响应内容类型不一致

问题：`Invoke-WebRequest` 的 `Content` 在不同响应下可能表现为字符串或字节数组，强制转换会导致客户端解析错误。

处理：先检查是否为 `[byte[]]`，只有字节数组才使用 UTF-8 解码；字符串直接解析。

结论：HTTP 已返回 2xx 不代表客户端后续解析一定成功，应区分服务器错误与本地脚本错误。

### 9.5 测试中的 ERROR 日志不等于测试失败

问题：验证未知异常安全处理时会故意记录异常堆栈。

处理：同时检查 Maven 最终汇总中的 Failures、Errors、Skipped 和 `BUILD SUCCESS`。

结论：日志级别和测试执行结果是不同维度。

### 9.6 Flyway 与 MySQL 版本警告

当前 Flyway 在连接 MySQL 8.4 时输出“已测试支持的最新版本为 MySQL 8.1”的升级建议。现有 V1 校验、迁移和集成测试均通过，因此本周不临时升级依赖；后续依赖治理时需要重新核对兼容矩阵并回归测试。

---

## 10. 主要技术取舍

### 模块化单体

选择单个 Spring Boot 应用，降低部署、调试和事务复杂度；按业务包保持模块边界。不为了简历提前拆微服务。

### DTO、Entity、Response 分离

避免客户端控制 `status`、`source`、`deleted` 等服务端字段，也避免将数据库内部结构直接暴露为 API。

### 业务校验与数据库约束双层保护

Service 提供业务语义和友好错误；MySQL 提供并发情况下的最终正确性。

### 软删除

保留财务类数据历史，为后续对账、审计和异常审核提供基础；相应代价是历史查询和唯一编号复用规则更复杂。

### 统一异常处理

Controller 不重复编写 `try/catch`，所有接口使用一致错误结构；未知异常不向客户端暴露内部细节。

### 先同步闭环，再增加中间件

Week 1 只完成同步账户和交易闭环。CSV、RabbitMQ、Redis 等技术只有在基础业务稳定后再引入，避免技术栈堆砌。

---

## 11. 面试问题与简答

### 1. Controller、Service、Mapper 分别负责什么？

Controller 处理 HTTP 接入，Service 实现业务规则与事务，Mapper 负责数据库访问和结果映射。

### 2. 为什么不直接用 Entity 接收和返回请求？

Entity 包含数据库内部字段。DTO 与 Response 分离可以限制客户端输入、隐藏内部状态，并减少数据库变化对 API 的影响。

### 3. 金额为什么使用 BigDecimal？

`double` 是二进制浮点数，无法精确表示部分十进制小数；`BigDecimal` 配合 `DECIMAL(19,2)` 可以精确保存财务金额。

### 4. 为什么交易唯一键包含 source？

同一账户、同一流水号需要允许 `MANUAL` 与 `CSV_IMPORT` 各存在一条，以便后续进行内外部数据对账。

### 5. Service 已经查重，为什么还需要数据库唯一约束？

预查重与写入之间存在并发窗口。Service 用于友好提示，数据库唯一约束负责最终正确性。

### 6. 什么是软删除？

不物理删除记录，而是将 `deleted` 改为 1。普通查询过滤该记录，但历史与唯一约束仍然保留。

### 7. 为什么事务边界放在 Service？

Service 知道一次完整业务操作包含哪些查询、判断和写入步骤，能够将它们作为一个整体提交或回滚。

### 8. 400、404、409、500 在项目中分别表示什么？

- 400：请求参数或格式错误；
- 404：目标资源不存在；
- 409：请求与当前数据或业务状态冲突；
- 500：未预期的服务器内部异常。

### 9. 为什么分页必须有稳定排序？

没有明确排序时数据库不保证返回顺序，翻页可能出现记录重复或遗漏。账户使用 `id DESC`，交易使用时间和 ID 双重降序。

### 10. 单元测试、Controller 测试和集成测试有什么区别？

- Service 单元测试隔离 Mapper，快速验证业务分支；
- Controller 测试验证路由、参数校验、状态码和 JSON；
- 集成测试连接真实 Spring 上下文和 MySQL，验证组件组合、Flyway、分页与错误处理。

---

## 12. 当前限制与下一步边界

当前尚未实现：

- Spring Security；
- JWT 登录；
- ADMIN / REVIEWER RBAC；
- CSV 文件上传与行级校验；
- SHA-256 文件去重；
- 自动对账；
- RabbitMQ 异步导入与可靠性机制；
- Redis 缓存与限流；
- 风险规则、异常审核和审计日志；
- Swagger/OpenAPI；
- Linux 部署、CI、监控、压测和安全报告。

Week 1 的空环境运行、自动化测试、真实 HTTP 验收、知识口述、Git 历史和综合基础检查均已完成，未发现需要阻塞 Week 2 的遗留缺陷。下一里程碑可以进入 Week 2，但不在本复盘文档中提前实现后续功能。
