# FinGuard Core

FinGuard Core 是一个面向 Java 后端实习项目训练的交易导入、自动对账与异常审核平台。

当前进度为 Week 5 Day 4 已完成：ADMIN/REVIEWER 可分页查询审核任务，REVIEWER 可携带期望版本确认或忽略；数据库条件更新保证并发请求只有一个成功，并稳定区分不存在、终态和版本冲突。完整 `mvn clean test` 为 326/326，真实 MySQL/RabbitMQ/JWT/HTTP 与双请求并发闭环已验证并清理。

## 当前技术基线

- Java 17
- Maven 3.9+
- Spring Boot 3.5.16
- Spring MVC
- Spring Boot Actuator
- Spring Security
- OAuth2 Resource Server / JWT
- BCrypt
- Jakarta Validation
- MyBatis-Plus 3.5.17
- MySQL 8.4.10
- RabbitMQ 4.3.4
- Flyway
- JUnit 5 / Spring Boot Test
- Mockito / MockMvc

## 已实现功能

- 账户创建、单条查询、改名、状态切换和软删除；
- 人工交易创建、单条查询、修改和软删除；
- `BigDecimal` 金额精度与范围校验；
- 固定 `Asia/Shanghai` 业务时区和未来五分钟容忍；
- `MANUAL` 交易来源由服务端设置；
- 基于 `(account_id, source, external_transaction_no)` 的组合唯一性；
- Service 预查重与数据库唯一约束双层保护；
- 账户和交易软删除后的历史编号保留；
- 账户按状态、类型和关键词分页查询；
- 交易按账户、方向、来源、流水号和时间范围分页查询；
- 用户、角色和用户角色关系的 Flyway 持久化；
- BCrypt 密码校验和两小时有效的 HS256 JWT；
- 无状态 Bearer Token 认证，不创建服务端 Session；
- `ADMIN` 可读写账户和交易，`REVIEWER` 只读；
- 统一的 `401` 认证失败和 `403` 权限不足响应；
- 统一错误响应以及 `400`、`404`、`409`、`500` 状态映射；
- Service 层读写事务边界和多表异常回滚验证；
- 基于真实 `EXPLAIN` 新增默认交易分页索引；
- 导入任务和行错误持久层，包含 SHA-256 文件哈希唯一约束、状态/错误码检查约束和稳定错误分页；
- CSV 文件请求检查、原始字节 SHA-256、严格 UTF-8/BOM 与 RFC 4180 结构解析；
- CSV 六字段规范化与业务校验、批量账户解析、文件内重复和数据库重复判断；
- 异步 CSV 受理、哈希幂等、原始字节持久化和任务/文件/Outbox 原子提交；
- RabbitMQ 异步导入消费、消息契约校验、持久化文件一致性检查、任务行锁、终态幂等、崩溃回滚恢复和提交后手动 ACK；
- 导入与对账统一的失败分类、5 秒/30 秒两级有限重试、可靠转交和独立 DLQ 隔离；
- 导入任务详情和行错误分页；`ADMIN` 可上传，`ADMIN`、`REVIEWER` 可查询；
- 对账任务和逐笔结果的 V6 持久层、外键/检查约束、导入任务唯一幂等和稳定结果分页；
- 强流水号优先、金额/方向/三天时间窗口补充的一对一两阶段对账，输出 `MATCHED`、`UNMATCHED`、`DUPLICATE` 和 `SUSPICIOUS`；
- 异步对账受理、真实消息消费、任务详情和结果类型筛选；`ADMIN` 可触发，`ADMIN`、`REVIEWER` 可查询；
- 500 条分批候选查询与结果写入、任务行锁、并发消息幂等、结果/统计原子提交、事务回滚恢复和 ACK 丢失红投短路；
- RabbitMQ durable 基础拓扑、持久化 JSON 消息、稳定消息 ID、correlated confirm、mandatory return、Outbox 发布退避和定时 Relay；
- 类型安全的风险规则配置与启动校验：大额阈值、5 分钟疑似重复窗口和 10 分钟高频支出窗口；
- 三条策略规则以固定原因码、观测值、阈值/窗口快照和安全摘要生成可解释 `risk_hits`；
- 历史 CSV 候选按账户/时间范围每 500 个账户分批查询，再按 `(transaction_time, id)` 在内存中稳定计算，不使用逐交易 SQL；
- V9 `review_tasks` 以真实外键区分对账异常与风险命中来源，由 CHECK、唯一键和 RESTRICT 外键保护来源、状态、幂等和证据关系；
- 对账 results、risk hits、review tasks 和 job `COMPLETED` 同事务提交，规则/风险/审核写入失败整体回滚，重复 MQ 和 ACK 丢失红投无重复副作用；
- 审核任务详情与稳定分页，支持状态、来源、对账异常类型和风险规则筛选；ADMIN/REVIEWER 可查，只有 REVIEWER 可决策；
- `PENDING → CONFIRMED/IGNORED` 使用 `id + PENDING + version` 原子条件更新，保存 JWT 审核人、审核时间和可选说明；并发失败、终态重复提交和不存在分别返回稳定 `409/409/404`；
- 326 个自动化测试，以及真实 MySQL、RabbitMQ、JWT、HTTP、分页、认证、RBAC、事务、索引、两级延迟重试、DLQ、风险生成、审核决策、乐观锁并发和应用健康验收。

## 本地运行

### 1. 确认环境

```powershell
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version
mvn -version
git --version
docker version
docker compose version
```

### 2. 准备本地配置

首次运行时复制配置示例：

```powershell
Copy-Item .env.example .env
```

然后在 `.env` 中设置仅供本机使用的 MySQL 和 RabbitMQ 密码。`.env` 已被 Git 忽略，不要提交真实密码。

应用启动还需要一个 Base64 编码、解码后不少于 32 字节的 JWT 密钥。可以只在当前 PowerShell 会话中生成：

```powershell
$jwtKeyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtKeyBytes) } finally { $rng.Dispose() }
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtKeyBytes)
```

不要把真实 JWT 密钥写入仓库、文档或命令输出。

### 3. 启动 MySQL 和 RabbitMQ

```powershell
docker compose up -d
docker compose ps
```

预期 `finguard-mysql` 最终显示为 `healthy`。

### 4. 运行测试

```powershell
mvn clean test
```

### 5. 启动应用

```powershell
mvn spring-boot:run
```

访问健康检查：

```text
GET http://localhost:8080/actuator/health
```

预期响应：

```json
{"status":"UP"}
```

## 当前接口

### 认证

```text
POST   /api/auth/login
```

登录和健康检查允许匿名访问。其余账户、交易接口必须携带合法的 Bearer Token。

### 账户

```text
POST   /api/accounts
GET    /api/accounts?page=1&size=20&status=ACTIVE&accountType=BANK&keyword=main
GET    /api/accounts/{accountId}
PATCH  /api/accounts/{accountId}/name
PATCH  /api/accounts/{accountId}/status
DELETE /api/accounts/{accountId}
```

账户的两个查询接口允许 `ADMIN`、`REVIEWER`，其余写接口仅允许 `ADMIN`。

### 交易

```text
POST   /api/transactions
GET    /api/transactions?page=1&size=20&accountId=1&direction=EXPENSE&source=MANUAL
GET    /api/transactions/{transactionId}
PUT    /api/transactions/{transactionId}
DELETE /api/transactions/{transactionId}
```

交易的两个查询接口允许 `ADMIN`、`REVIEWER`，其余写接口仅允许 `ADMIN`。

当前交易创建接口只创建 `MANUAL` 交易。`CSV_IMPORT` 只由 CSV 导入流程内部创建，客户端不能自行指定交易来源。

分页接口默认 `page=1`、`size=20`，每页最多返回 100 条记录。交易查询还支持 `externalTransactionNo`、`startTime` 和 `endTime` 条件。

### CSV 导入

```text
POST   /api/import-jobs
GET    /api/import-jobs/{importJobId}
GET    /api/import-jobs/{importJobId}/errors?page=1&size=20
```

上传接口使用 `multipart/form-data` 的 `file` 字段，只允许 `ADMIN`，文件最大 5 MiB。首次接收某组原始字节时原子保存 `PENDING` 任务、原始文件和 Outbox，返回 `202 + Location`；再次上传相同字节时返回已有任务的 `200`，且 `duplicateFile=true`。任务详情和错误分页允许 `ADMIN`、`REVIEWER` 查询。

### 自动对账

```text
POST   /api/reconciliation-jobs
GET    /api/reconciliation-jobs/{reconciliationJobId}
GET    /api/reconciliation-jobs/{reconciliationJobId}/results?page=1&size=20&resultType=MATCHED
```

触发接口接收 JSON `{"importJobId": 1}`，只允许 `ADMIN`。首次受理时原子保存 `PENDING` 任务和 Outbox，返回 `202 + Location`；相同导入任务再次或并发触发返回已有任务的 `200`，且 `duplicateRequest=true`。任务详情和结果分页允许 `ADMIN`、`REVIEWER` 查询。

第一版只比较同账户、未删除的 `CSV_IMPORT` 与 `MANUAL` 交易：优先使用大小写敏感的外部流水号，再使用方向、精确金额和前后 3 天时间窗口。系统不修改原交易，逐笔保存匹配方式和稳定原因码。

### 异常审核

```text
GET    /api/review-tasks?page=1&size=20&status=PENDING&sourceType=RISK_HIT&ruleCode=LARGE_AMOUNT
GET    /api/review-tasks/{reviewTaskId}
PATCH  /api/review-tasks/{reviewTaskId}/decision
```

列表和详情允许 `ADMIN`、`REVIEWER` 查询；决策只允许 `REVIEWER`。决策请求示例为 `{"decision":"CONFIRMED","version":0,"note":"verified"}`，成功返回更新后的任务。列表还支持 `resultType=UNMATCHED/DUPLICATE/SUSPICIOUS`；`resultType` 与 `ruleCode` 是来源专属且互斥的筛选条件。

审核只修改 `review_tasks`，不会改写原交易、对账结果或风险命中。两个请求携带同一版本并发决策时只有一个能完成条件更新，失败方返回 `409 REVIEW_VERSION_CONFLICT`；已经进入终态的任务返回 `409 INVALID_REVIEW_OPERATION`。

错误响应统一包含 `timestamp`、`status`、`code`、`message`、`path` 和 `fieldErrors`。例如：

```json
{
  "timestamp": "2026-07-23T12:00:00Z",
  "status": 404,
  "code": "ACCOUNT_NOT_FOUND",
  "message": "Account not found: 99",
  "path": "/api/accounts/99",
  "fieldErrors": []
}
```

## 当前限制

- 审核分页、详情、决策和乐观锁已完成，但尚未实现审核撤销/重开、批量审核或审计日志；
- 导入和对账消费者采用单事务任务行锁，正常处理中间态不会独立提交，也不提供跨事务可见的处理租约；
- DLQ 目前依赖运维排查，尚未提供失败任务的人工重跑、覆盖导入或管理接口；
- 风险候选查询在 4,000 条合成数据上由 MySQL 优化器选择全表扫描；当时估算命中 15.89% 且数据量小，Day 3 未根据单次合成样本追加索引，留待 Week 6 用更真实数据规模压测后决定；
- 不提供复杂模糊匹配、金额容差或人工确认；Redis 尚未引入。

## 当前范围

项目范围、学习方式和六周计划以 [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) 为唯一事实来源。后续每次只推进一个可运行、可测试、可提交的小里程碑。
