# Week 5 Day 6：Redis 统计缓存与固定窗口限流任务安排

## 1. 本日结论

- **进入基线**：Week 5 Day 5 已在提交 `f58ca6e` 完成；当前数据库最新迁移为 V10，共 14 张业务表，风险、审核、乐观锁和五类审计均已落地。
- **测试基线**：进入本日时最近一次完整 `mvn clean test` 为 344/344；这只是历史基线，Day 6 实现完成后必须重新实跑并记录新数字。
- **业务目标**：新增一个全局统计概览，用 Redis 缓存 MySQL 聚合结果；为匿名登录和 ADMIN CSV 上传增加原子固定窗口限流；Redis 故障时降级但不破坏核心业务正确性。
- **数据真源**：MySQL 继续保存全部业务事实。Redis 只保存可重建统计快照和短期计数器，不参与导入、对账、风险、审核、审计或幂等判定。
- **迁移边界**：Day 6 不新增 Flyway 迁移，也不修改 V1～V10；实现后最新版本仍为 V10、业务表仍为 14 张。

## 2. 为什么这样安排

Day 5 之后，系统已经能稳定完成“导入 → 对账 → 风险识别 → 人工审核 → 审计留痕”，但还有两个外围问题：

1. 管理人员想看全局概况时，应用每次都要重新统计多张表；
2. 匿名登录和 CSV 上传缺少入口频率保护。

Redis 适合处理这两类“丢失后可重建”的短期数据，但不适合替代 MySQL 中的业务真源。因此本日采用以下原则：

```text
统计读取
  → 先读固定 Redis key
  → 命中：直接返回原统计快照
  → 未命中/Redis 故障：从 MySQL 聚合
  → Redis 可用时写入 60 秒缓存

业务写入
  → 在原有 MySQL 事务内完成业务变更
  → 事务成功提交
  → AFTER_COMMIT 删除统计 key
  → 删除失败只告警，最多由 60 秒 TTL 纠正

登录/上传
  → Redis Lua 原子增加固定窗口计数并读取剩余 TTL
  → 阈值内：进入现有业务流程
  → 超阈值：429 + Retry-After
  → Redis 故障：fail-open，继续现有流程
```

这个取舍优先保证实习版项目的范围、可解释性和核心业务可用性，不建设通用缓存或网关限流平台。

## 3. 本日必须掌握的知识点

### 3.1 缓存旁路模式

读取由应用显式执行“缓存 → 数据库 → 回填缓存”。写路径不直接把新聚合结果写进 Redis，而是在业务事务提交后删除旧快照，让下一次查询重建。

本日只承诺最终一致：提交后失效加 60 秒 TTL 能限制陈旧时间，但并发的“查询回填”和“业务提交删除”仍可能发生竞态，因此不能宣传为强一致缓存。

### 3.2 AFTER_COMMIT 失效

在业务事务内提前删除 key 会产生错误窗口：如果事务随后回滚，缓存已被删除；如果其他请求在事务提交前回填，又可能放回旧数据。

本日使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 或等价事务同步：业务 Service 在事务内发布“统计已变化”事件，只有提交成功后监听器才执行幂等 `DEL`。回滚不得触发删除。

### 3.3 Redis Lua 原子性

限流必须把 `INCR`、首次设置 TTL、读取剩余 TTL 放在同一段 Lua 中执行：

```text
count = INCR key
if count == 1 then PEXPIRE key windowMillis end
ttl = PTTL key
if ttl == -1 then PEXPIRE key windowMillis; ttl = windowMillis end
return count, ttl
```

第 1～limit 次允许，第 limit+1 次开始拒绝。窗口由第一次请求启动；被拒绝的请求可以继续计数，但不能延长原 TTL。`Retry-After = max(1, ceil(ttlMillis / 1000))`。

### 3.4 fail-open 的代价

Redis 连接失败或命令超时时：统计直接查 MySQL；登录和上传继续执行。这样 Redis 故障不会阻断核心业务，但故障期间反暴力破解和上传频控会暂时失效。日志只能记录安全的降级类型和操作名，不能记录密码、JWT、原始 key、明文用户名或 CSV 内容。

## 4. 范围边界

### 4.1 本日实现

- Redis Docker 服务、Spring Data Redis 依赖、连接/超时/密码/业务参数配置；
- `GET /api/statistics/overview`、MySQL 聚合、显式 JSON 缓存和 RBAC；
- 一个固定统计 key 的 60 秒 TTL 和相关业务提交后的缓存失效；
- 登录与上传两个固定窗口限流场景；
- 统一 `429 RATE_LIMIT_EXCEEDED` 与 `Retry-After`；
- 缓存命中、未命中、失效、TTL、原子并发、窗口恢复、权限和 Redis 故障降级测试；
- 真实 MySQL、RabbitMQ、Redis、JWT、HTTP 验收与清理。

### 4.2 本日不实现

- Redis Session、JWT 黑名单、刷新令牌、验证码、账户锁定或密码失败数据库计数；
- Redis 分布式锁、Redisson、布隆过滤器、热点 key 自动发现、多级缓存或通用缓存框架；
- 滑动窗口、令牌桶、漏桶、网关限流、跨服务全局配额或动态规则后台；
- 缓存审核详情、进行中任务详情、原始 CSV、审计原文或任意用户输入；
- 使用 Redis 代替数据库唯一键、事务、任务行锁、审核 version 或审计唯一约束；
- 修改 V1～V10、新 MQ 事件、前端、Week 6 指标告警或部署能力。

## 5. Redis 基础设施与配置

### 5.1 依赖和容器

- `pom.xml` 新增 `spring-boot-starter-data-redis`，使用 Spring Boot 管理的 Lettuce 客户端，不单独覆盖客户端版本；
- `docker-compose.yml` 新增仅绑定本机端口的 Redis 服务、持久化项目专属数据卷和健康检查；
- 实施时固定一个实际拉取并验证过的 Redis 8.x Alpine patch 版本，记录真实版本，不使用 `latest`；
- 密码只从 `.env` 注入，`.env.example` 只保留占位值；健康检查通过环境变量向 `redis-cli` 提供密码，避免把密码写进命令输出；
- Redis 故障不应阻止 Spring Boot 进程启动；连接按需建立并使用短连接/命令超时。Redis 停机时 Actuator 可以报告依赖不健康，但 fail-open 接口仍要按本契约工作。

### 5.2 候选配置

```text
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=本地密码
FINGUARD_REDIS_CONNECT_TIMEOUT=1s
FINGUARD_REDIS_COMMAND_TIMEOUT=1s
FINGUARD_STATISTICS_CACHE_TTL=60s
FINGUARD_RATE_LIMIT_LOGIN_LIMIT=5
FINGUARD_RATE_LIMIT_LOGIN_WINDOW=300s
FINGUARD_RATE_LIMIT_UPLOAD_LIMIT=10
FINGUARD_RATE_LIMIT_UPLOAD_WINDOW=60s
```

业务配置使用 `@ConfigurationProperties` 和 Jakarta Validation：TTL、窗口和阈值必须为正，并设置合理上限以防误配置导致长期封禁或内存占用。测试配置可缩短 TTL/窗口，但生产默认值必须符合 Day 1 契约。

### 5.3 序列化与 key 命名

- 统计 key：`finguard:statistics:overview:v1`；
- 登录 key：`finguard:ratelimit:login:v1:{ipHash}:{usernameHash}`；
- 上传 key：`finguard:ratelimit:upload:v1:user:{userId}`；
- value 使用 UTF-8 JSON 或数字字符串，不使用 JDK 原生对象序列化；
- 统计响应结构发生不兼容变化时使用新版本 key，不能尝试把旧 Java 类型反序列化为新类型；
- 用户名先复用现有 `AuthInputNormalizer` 规范化，再与 remote address 分别计算小写 SHA-256 十六进制摘要。摘要只避免明文出现在 key/日志中，不宣传为不可逆匿名化；
- 只使用 `HttpServletRequest.getRemoteAddr()`。未建立可信代理配置前不得相信客户端提供的 `X-Forwarded-For`。

## 6. 统计接口与缓存设计

### 6.1 HTTP 契约

```text
GET /api/statistics/overview
```

允许 `ADMIN`、`REVIEWER`，匿名返回 401，无已知角色返回 403。响应保持 Day 1 已锁定的五组统计：

- import jobs：total、pending、processing、success、partialSuccess、failed；
- reconciliation jobs：total、pending、processing、completed、failed；
- reconciliation results：matched、unmatched、duplicate、suspicious；
- risk hits：total、largeAmount、possibleDuplicate、frequentTransaction；
- review tasks：pending、confirmed、ignored；
- generatedAt：MySQL 快照生成时的 `Asia/Shanghai` 时间，缓存命中时保持不变。

空表返回 0，不返回 `null`。各状态分组必须与 total 守恒；未知数据库枚举值不能被静默忽略为合法统计。

### 6.2 查询与缓存流程

```text
StatisticsController
  → StatisticsOverviewService
    → StatisticsOverviewCache.get()
      → hit：反序列化显式 DTO 并返回
      → miss / Redis DataAccessException：StatisticsMapper 聚合 MySQL
        → 组装不可变 StatisticsOverviewResponse
        → Redis 可用时 SET key JSON TTL 60s
        → 返回响应
```

不使用 `@Cacheable` 隐藏 key、序列化和故障边界；用一个受限缓存组件显式实现读、写和删除，便于测试 fail-open 和审计 key 内容。缓存中出现无法解析的 JSON 时记录无敏感内容的 WARN、尝试删除该 key 并按 miss 查询 MySQL；缓存 miss 后若数据库聚合异常，仍沿用 500，不能把失败结果写进缓存。

Mapper 使用有限的 `COUNT/GROUP BY` 聚合，不加载整表实体，不对每个状态分别发一条 SQL。实现前用真实 MySQL 与现有索引执行 `EXPLAIN`；没有真实依据不新增 V11 或猜测型索引。

### 6.3 失效事件

以下变化成功提交后发布统一的 `StatisticsChangedEvent` 并删除同一个统计 key：

- 导入任务首次受理；
- 导入任务进入 SUCCESS、PARTIAL_SUCCESS 或 FAILED；
- 对账任务首次受理、进入 COMPLETED 或 FAILED；
- 对账完成事务写入 reconciliation results、risk hits 和 review tasks；
- 审核任务成功变为 CONFIRMED 或 IGNORED。

同一事务内多个变化允许发布多个事件，因为 `DEL` 天然幂等；也可以在事务编排层合并为一个事件，但不能为了去重引入 Redis 锁。重复文件、重复 MQ、已完成任务短路和失败的审核并发请求没有新业务提交，不应产生新的失效副作用。

## 7. 固定窗口限流设计

### 7.1 通用组件

`FixedWindowRateLimiter` 只接收已经构造好的安全 key、正阈值和窗口，调用类路径 Lua 脚本并返回 `allowed` 与 `retryAfterSeconds`。Lua 返回值、空 TTL、负 TTL、连接失败和命令超时均要有确定处理；只有 Redis 基础设施失败走 fail-open，配置错误或代码契约错误必须快速失败，不能全部用 `catch (Exception)` 吞掉。

超限抛出 `RateLimitExceededException`，由全局异常处理器返回：

```text
HTTP 429 Too Many Requests
Retry-After: 正整数秒
```

```json
{
  "status": 429,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Request rate limit exceeded",
  "path": "/api/auth/login",
  "fieldErrors": []
}
```

响应不得暴露当前计数、阈值、Redis key、用户名是否存在或内部异常。

### 7.2 登录限流

| 项目 | 决策 |
|---|---|
| 路径 | `POST /api/auth/login` |
| 默认阈值/窗口 | 5 次 / 300 秒 |
| 计数维度 | remote address 摘要 + 规范化用户名摘要 |
| 执行时机 | DTO 校验后、密码验证和 JWT 签发前 |
| 失败凭据/禁用用户 | 保留计数 |
| 成功登录 | JWT 成功签发后删除当前 key |
| Redis 故障 | fail-open，继续现有认证 |

`AuthController` 负责取得 remote address，`LoginRateLimitGuard` 复用现有用户名规范化规则并返回一个不向外暴露原始 key 的 permit。随后调用原有 `AuthService`；只有成功返回 `LoginResponse` 才重置该窗口。不存在用户、错误密码和禁用用户继续统一返回 `401 INVALID_CREDENTIALS`，不能因 key 或响应差异帮助枚举账户。

### 7.3 上传限流

| 项目 | 决策 |
|---|---|
| 路径 | `POST /api/import-jobs` |
| 默认阈值/窗口 | 10 次 / 60 秒 |
| 计数维度 | 已验签 JWT subject 对应 userId |
| 执行时机 | Spring Security 完成 ADMIN 授权后，文件读取、哈希、解析和数据库访问前 |
| 重复/缺失/无效文件 | 消耗配额 |
| 匿名/REVIEWER | 401/403，不能消耗 ADMIN 配额 |
| Redis 故障 | fail-open，继续现有上传 |

上传 Controller 进入方法后先解析可信 JWT subject，再调用 `UploadRateLimitGuard`，然后才检查 multipart file 并读取 bytes。Servlet 在 Controller 前拒绝的超大 multipart 可能不会计数，这是本日接受的限制，现有 5 MiB/6 MiB multipart 上限仍负责资源保护。

## 8. 文件与实现顺序

### 8.1 关键文件

```text
docs/design/week5-day6-redis-cache-and-rate-limit-design.md
TASKS.md
pom.xml
docker-compose.yml
.env.example
src/main/resources/application.yml
src/main/resources/redis/fixed-window-rate-limit.lua
src/main/java/com/finguard/core/redis/
src/main/java/com/finguard/core/statistics/
src/main/java/com/finguard/core/ratelimit/
src/main/java/com/finguard/core/auth/controller/AuthController.java
src/main/java/com/finguard/core/importjob/controller/ImportJobController.java
src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java
src/main/java/com/finguard/core/common/exception/ErrorCode.java
src/main/java/com/finguard/core/common/exception/GlobalExceptionHandler.java
src/main/java/com/finguard/core/importjob/service/impl/
src/main/java/com/finguard/core/reconciliation/service/impl/
src/main/java/com/finguard/core/review/service/impl/
src/test/java/com/finguard/core/statistics/
src/test/java/com/finguard/core/ratelimit/
src/test/java/com/finguard/core/auth/security/RbacAuthorizationIntegrationTest.java
```

### 8.2 建议执行顺序

1. 复核 Day 1 Redis 契约、Day 5 真实代码、所有相关事务入口和当前 344/344 基线；先写本设计文档，不先改运行时代码。
2. 新增 Spring Data Redis、Compose 服务、外置密码、连接超时和类型安全配置；验证真实 Redis 连接、认证、TTL 和 Actuator 状态。
3. 先实现显式 Redis 基础组件、JSON 序列化边界和 Lua 固定窗口原子算法，并用真实 Redis 集成测试锁定行为。
4. 实现统计 Mapper、响应模型、Service、Controller 和权限；验证首次 miss、第二次 hit、TTL 到期重建与 Redis 故障查 MySQL。
5. 在导入受理/终态、对账受理/终态、风险/审核生成和审核决策事务中发布统计变化事件；证明只在提交后删除，回滚不删除。
6. 接入登录限流：DTO 校验后计数、失败保留、成功清除，并保持统一 401 防枚举响应。
7. 接入上传限流：ADMIN 授权后、文件读取前计数；覆盖匿名/REVIEWER 不消耗配额、重复/无效文件消耗配额。
8. 增加统一 429、`Retry-After`、配置失败、缓存损坏、并发限流和故障降级测试；完整回归既有认证、导入、对账、审核和审计行为。
9. 启动真实 MySQL/RabbitMQ/Redis/Java 17 应用，完成 JWT/HTTP/SQL/Redis 端到端验收；暂停并恢复 Redis 验证降级与恢复。
10. 清理业务数据、8 个 RabbitMQ 队列、全部 `finguard:*` 验收 key、临时凭据和端口；执行敏感信息检查、Week 6/Day 7 范围审计和 `git diff --check`，回填真实数字后再提交。

## 9. 测试矩阵

### 9.1 配置与基础组件

- 缺失/错误 Redis 密码、非法 TTL、零/负阈值、零/负窗口启动失败；
- JSON 不使用 JDK 序列化，key 中没有明文用户名、密码、JWT 或文件信息；
- Lua 首次设置 TTL，已有窗口不续期，`PTTL=-1` 自动补 TTL；
- Redis 返回异常形状、超时和连接失败有确定行为。

### 9.2 统计缓存

- 五组空数据返回全 0，非空数据各状态统计与 MySQL 一致且守恒；
- 第一次 miss 查询 DB 并缓存，第二次 hit 不重复查询 DB，generatedAt 不变化；
- 60 秒 TTL 到期后重建；损坏 JSON 删除后按 miss 重建；
- 业务提交成功后 key 被删除，事务回滚不删除；
- Redis 读失败、写失败、删除失败分别按契约降级，不把 Redis 异常映射为业务 500；
- DB 聚合失败不写缓存并沿用统一 500；
- ADMIN/REVIEWER 200、匿名 401、无已知角色 403。

### 9.3 限流

- 登录第 1～5 次进入认证，第 6 次为 429；失败凭据保留计数，成功 JWT 后清除窗口；
- 不同 IP 或用户名摘要互不影响，不存在用户与错误密码响应保持相同；
- 上传第 1～10 次进入业务，第 11 次为 429；同一用户窗口结束后恢复，不同 ADMIN 独立；
- 匿名上传 401、REVIEWER 上传 403 且 Redis 中没有对应上传 key；
- 多线程同一 key 同时请求时，Lua 计数无丢失，成功数不超过阈值；
- `Retry-After` 始终为正且不超过当前剩余窗口的向上取整边界；
- Redis 停止后登录和上传继续，恢复后新窗口正常工作；数据库唯一键和原有文件哈希幂等仍防止重复副作用。

### 9.4 回归与真实验收

- Redis、MySQL、RabbitMQ 均 healthy，Java 17 应用启动且全依赖可用时 `/actuator/health` 为 `UP`；
- 真实 ADMIN/REVIEWER JWT 查询统计，Redis CLI 核对固定 key、JSON 和 TTL；
- 真实业务提交后统计 key 消失，下一次 GET 返回更新后的 MySQL 统计并重新缓存；
- 真实登录/上传超过阈值得到 `429 + Retry-After`，窗口结束后恢复；
- 停止 Redis 后统计仍从 MySQL 返回，登录/上传 fail-open；恢复 Redis 后没有重复交易、任务、风险、审核或审计记录；
- 聚焦测试与完整 `mvn clean test` 均为 0 failures、0 errors、0 skipped，实际总数只能在实施后填写，不能预填；
- 测试数据、Redis key、RabbitMQ 消息、临时密码/JWT 和端口全部清理。

## 10. 验收标准

只有以下条件全部满足，Day 6 才能关闭：

- [x] 设计、配置、代码和测试与 Day 1 契约一致，V1～V10 checksum 未改变且没有 V11；
- [x] Redis 使用外置密码、固定镜像、健康检查、短超时和显式字符串/JSON序列化，仓库无真实凭据；
- [x] 统计接口只允许 ADMIN/REVIEWER，五组统计来自 MySQL，缓存 key/TTL/generatedAt 符合契约；
- [x] 缓存首次 miss、后续 hit、TTL、损坏值、提交后失效、回滚不失效和 Redis 故障降级均有自动化证据；
- [x] 登录和上传固定窗口由同一 Lua 原子算法实现，阈值、窗口、key 维度和计数时机完全符合契约；
- [x] 429 使用统一错误体并包含合法 `Retry-After`，响应和日志不泄漏敏感输入或 Redis key；
- [x] 匿名/无权上传不消耗 ADMIN 配额，成功登录清除自己的窗口，失败登录保留计数且不破坏防枚举语义；
- [x] Redis 停机时统计、登录和上传按 fail-open 契约运行，数据库不变量和 RabbitMQ 流程保持正确；
- [x] 完整回归不少于历史 344 项且零失败/错误/跳过，真实 HTTP/MySQL/Redis/RabbitMQ 验收和清理完成；
- [x] README、TASKS 和实际实现一致，敏感信息检查、范围审计与 `git diff --check` 通过。

## 11. 常见错误预防

- 不要用 Redis 保存业务最终状态，也不要用缓存命中代替权限或数据库查询正确性；
- 不要在 MySQL 事务提交前删除统计 key，不要在回滚路径发布可执行的失效事件；
- 不要在缓存中使用 JDK 原生序列化，不要把任意用户输入拼入 key；
- 不要用 `INCR` 和 `EXPIRE` 两条非原子命令制造无 TTL 的永久 key；
- 不要信任未配置可信代理时的 `X-Forwarded-For`；
- 不要在 Spring Security 授权前消耗上传配额，也不要让登录限流暴露用户名是否存在；
- 不要用 `catch (Exception)` 把配置错误、JSON 代码缺陷或数据库异常伪装成 Redis fail-open；
- 不要因为 Redis 停机就返回 500 或回滚已经提交的 MySQL 业务；
- 不要为一个全局 60 秒 key 引入分布式锁、布隆过滤器或缓存预热平台；
- 不要预填测试数字、伪造缓存命中率或根据单次小样本宣称性能提升。

## 12. 回滚

- Java、配置、测试和设计文档可按 Day 6 文件范围回退；回退后核心 MySQL/RabbitMQ 导入、对账、风险、审核和审计能力必须继续运行；
- Day 6 没有数据库迁移，不得修改或删除 V8～V10，也不得删除风险、审核或审计数据来回滚 Redis；
- 若 Redis 服务已声明，只删除本项目明确命名且确认可重建的 `finguard:*` key；不得清空其他应用的 Redis 数据库或删除 MySQL/RabbitMQ 数据卷；
- Compose 回滚只移除 Redis 服务和本项目专属 Redis volume；执行任何卷操作前必须确认精确项目名、卷名和可重建性；
- Redis 故障或回滚后，统计接口可直接查 MySQL，限流暂时失效，这是已记录的降级状态，不影响数据库最终正确性。

## 13. 实施与验收结论

- 实际基础设施为 Redis 8.2.8 Alpine、Spring Data Redis/Lettuce、外置密码、AOF、短连接/命令超时和项目专属 volume；真实容器健康检查通过。
- 五条 `COUNT/GROUP BY` 已在真实 MySQL 8.4.10 执行 `EXPLAIN`；现有数据规模下部分小表全表扫描、其余查询使用覆盖索引，没有依据新增 V11 或猜测型索引。Flyway 仍为 V10，业务表仍为 14 张。
- Redis/统计/限流专项测试 31/31，最终完整 `mvn clean test` 366/366，均为 0 failures、0 errors、0 skipped。
- Java 17 真实应用健康为 `UP`；匿名/ADMIN/REVIEWER 统计权限、60 秒缓存、命中保持 `generatedAt`、提交后失效、真实 RabbitMQ 导入终态和 MySQL 统计重建均通过。
- 登录第 6 次、上传第 11 次均得到 `429 + Retry-After`；暂停 Redis 后统计回源 MySQL，认证和授权继续按原语义运行，恢复后缓存重新建立。
- 验收用户、导入任务、Outbox/审计关联数据、8 个 RabbitMQ 队列消息、全部 `finguard:*` key、临时 JWT/密码、应用进程和 18080 端口均已清理。

## 14. 提交建议

```text
feat: add redis statistics cache and rate limits
```
