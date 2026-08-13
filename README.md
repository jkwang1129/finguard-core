# FinGuard Core

FinGuard Core 是一个面向 Java 后端实习项目训练的交易导入、自动对账与异常审核平台。

当前进度为 Week 6 Day 6 已完成：安全源码/依赖/镜像/配置/秘密与被动 Web 基线已核验，1 个开发 Compose Low 端口暴露问题已修复，Redis、RabbitMQ 和错误 MySQL 配置三次隔离故障演练全部恢复，Java 17 完整回归为 379/379。详细证据见 [Week 6 Day 6 安全报告](docs/review/week6-day6-security-report.md) 和 [Week 6 Day 6 故障演练复盘](docs/review/week6-day6-fault-drills.md)。

## 当前技术基线

- Java 17
- Maven 3.9+
- Spring Boot 3.5.16
- Spring MVC
- Spring Boot Actuator
- Micrometer Prometheus Registry
- Prometheus 3.7.3
- Grafana 12.3.1
- Springdoc OpenAPI 2.8.17 / Swagger UI
- Spring Security
- OAuth2 Resource Server / JWT
- BCrypt
- Jakarta Validation
- MyBatis-Plus 3.5.17
- MySQL 8.4.10
- RabbitMQ 4.3.4
- Redis 8.2.8 / Spring Data Redis / Lettuce
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
- V10 `audit_logs` 以真实外键、CHECK、唯一键和稳定分页索引保护五类白名单事件；受限 Mapper 不开放审计更新或删除；
- CSV 首次受理、导入失败、对账完成、审核确认和忽略使用固定安全摘要，并与对应业务事实同事务提交；重复文件、重复消息和失败的并发审核不重复记录；
- `GET /api/audit-logs` 支持 `actionCode`、`initiatedBy` 和稳定分页，仅允许 `ADMIN` 查询；
- `GET /api/statistics/overview` 聚合导入、对账、风险与审核状态，以显式 JSON 缓存到固定 Redis key 60 秒；ADMIN/REVIEWER 可查，事务成功提交后失效，Redis 异常时回源 MySQL；
- 登录按 remote address 摘要与规范化用户名摘要限制为 5 次/300 秒，成功签发 JWT 后清除当前窗口；CSV 上传按已验签 userId 限制为 10 次/60 秒；
- 两类限流共用 Lua 原子 `INCR + PEXPIRE + PTTL`，超限统一返回 `429 RATE_LIMIT_EXCEEDED + Retry-After`，Redis 异常时 fail-open；
- 提交后且去重的导入终态 Counter、对账处理 Timer 和有限 `flow/reason` 标签的消息失败 Counter；
- Prometheus 自动抓取 HTTP/JVM/HikariCP/业务指标，Grafana 自动配置数据源和 9 面板 FinGuard 仪表盘；
- 378 个自动化测试，以及真实 MySQL、RabbitMQ、Redis、JWT、HTTP、OpenAPI/Swagger、分页、认证、RBAC、事务、索引、两级延迟重试、DLQ、风险生成、审核决策、审计一致性、缓存失效、限流、乐观锁并发、监控采集和应用健康验收。

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

然后在 `.env` 中设置仅供本机使用的 MySQL、RabbitMQ、Redis 和 Grafana 密码。`.env` 已被 Git 忽略，不要提交真实密码。示例中的 `replace-with-*` 只能作为提示，不能直接用于真实环境。

应用启动还需要一个 Base64 编码、解码后不少于 32 字节的 JWT 密钥。可以只在当前 PowerShell 会话中生成：

```powershell
$jwtKeyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtKeyBytes) } finally { $rng.Dispose() }
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtKeyBytes)
```

不要把真实 JWT 密钥写入仓库、文档或命令输出。

### 3. 选择启动方式

#### 方式 A：宿主机运行应用

只启动三个依赖：

```powershell
docker compose up -d --wait mysql rabbitmq redis
docker compose ps
```

预期 `finguard-mysql`、`finguard-rabbitmq` 和 `finguard-redis` 最终均显示为 `healthy`。

运行完整测试和打包：

```powershell
mvn clean package
```

启动应用：

```powershell
mvn spring-boot:run
```

宿主机模式下，MySQL、RabbitMQ 和 Redis 默认连接 `127.0.0.1` 的映射端口。

#### 方式 B：完整 Compose

先确认当前 PowerShell 中已经设置 `JWT_SECRET_BASE64`，且 `.env` 已填写 Grafana 密码，然后构建并启动应用、三个依赖和两个监控服务：

```powershell
docker compose up -d --build --wait
docker compose ps
```

预期 `app`、`mysql`、`rabbitmq`、`redis`、`prometheus` 和 `grafana` 六个服务都显示为 `healthy`。应用容器通过 Compose 服务名连接依赖；应用、Prometheus 和 Grafana 默认分别只在本机 `127.0.0.1:8080`、`127.0.0.1:9090` 和 `127.0.0.1:3000` 暴露端口。

单独构建应用镜像：

```powershell
docker compose build app
```

查看应用/监控日志或重启应用：

```powershell
docker compose logs --tail=200 app
docker compose logs --tail=200 prometheus grafana
docker compose restart app
```

停止容器但保留 MySQL、RabbitMQ、Redis、Prometheus 和 Grafana 数据卷：

```powershell
docker compose down
```

`docker compose down -v` 会删除五个命名数据卷及其中数据，不属于日常停止操作；只有确认数据可丢弃时才能执行。

### 4. 验证应用

访问健康检查：

```text
GET http://localhost:8080/actuator/health
```

预期响应：

```json
{"status":"UP"}
```

OpenAPI 和 Swagger UI：

```text
GET http://localhost:8080/v3/api-docs
GET http://localhost:8080/swagger-ui/index.html
```

Swagger UI 可以匿名打开；先调用登录接口取得 JWT，再使用右上角 `Authorize` 配置 Bearer Token。文档端点公开不代表业务端点公开，实际读写权限仍由 Spring Security 的 ADMIN/REVIEWER RBAC 控制。

### 5. 验证监控

Prometheus 抓取端点允许匿名读取，但 Actuator 仍只暴露 `health` 和 `prometheus`；`env`、`beans`、`heapdump` 等敏感端点没有暴露。应用端口默认只绑定本机回环地址。

```text
GET http://localhost:8080/actuator/prometheus
GET http://localhost:9090/-/healthy
GET http://localhost:9090/api/v1/targets
GET http://localhost:3000/api/health
```

打开 `http://localhost:3000`，使用 `.env` 中的 `GRAFANA_ADMIN_USER` 和 `GRAFANA_ADMIN_PASSWORD` 登录。Prometheus 数据源和 `FinGuard / FinGuard Core Overview` 仪表盘会通过仓库中的 provisioning 文件自动创建，不需要在页面中手工添加。

仪表盘包括：

- Prometheus target 状态；
- HTTP 吞吐、5xx 错误率和 P95 延迟；
- JVM 内存与 HikariCP 连接池；
- CSV 导入成功、部分成功和失败数；
- 对账处理次数和 P95；
- 导入/对账消息消费失败次数。

三个自定义业务指标为：

```text
finguard_import_jobs_completed_total{outcome="success|partial_success|failed"}
finguard_reconciliation_processing_seconds_*{outcome="completed|failed"}
finguard_messaging_consumer_failures_total{flow="import|reconciliation",reason="..."}
```

导入 Counter 只在新终态事务提交后增加，重复消息不会重复增加。消息失败 Counter 表示失败处理尝试次数，不表示唯一失败消息数。指标标签不会使用用户 ID、任务 ID、交易 ID、消息 ID、文件名、用户名、JWT 或异常文本。

## Linux 单机部署

Linux 部署使用 [`compose.linux.yml`](compose.linux.yml)，不在服务器编译源码。GitHub Actions 只有在 `main` 的完整测试和镜像检查通过后，才发布：

```text
ghcr.io/jkwang1129/finguard-core:<完整 40 位 Git SHA>
```

不使用 `latest` 作为部署或回滚版本。应用镜像是私有 GHCR 包；服务器拉取凭据只需要 classic PAT 的 `read:packages`，不应使用仓库写权限或 GHCR 写权限 Token。

### 1. 准备部署目录

把以下版本化文件复制到授权 Linux 主机的固定目录：

```text
compose.linux.yml
.env.linux.example
ops/
scripts/linux/
```

创建服务器私有环境文件：

```bash
cp .env.linux.example .env.linux
chmod 600 .env.linux
```

编辑 `.env.linux`：

- 把 `FINGUARD_APP_IMAGE` 改成已通过 CI 的完整 SHA 镜像；
- 生成不同的 MySQL root/app、RabbitMQ、Redis 和 Grafana 强随机密码；
- 生成随机 32 字节以上的 Base64 JWT 密钥；
- 首次启动需要创建 ADMIN/REVIEWER 时，临时打开 Bootstrap 并填写强随机凭据。

`.env.linux` 已被 Git 忽略。不要把文件内容、Registry Token 或真实主机信息复制到 Issue、Actions 日志、截图或验收文档。

### 2. 主机预检

目标 Linux 需要 Docker Engine、Docker Compose Plugin 和 `curl`。安装命令应按实际发行版使用 [Docker 官方说明](https://docs.docker.com/engine/install/)，不要混用不同发行版的软件源。

```bash
sh scripts/linux/preflight.sh
```

预检只读取 Linux/资源/时间/Docker/端口/防火墙提示和 Compose 配置，不安装软件、不修改防火墙、也不渲染环境变量值。若主机已有其他项目，先确认容器和端口归属。

### 3. 拉取并启动

先在安全会话中注入 GHCR 用户名和只读 Token，再启动：

```bash
export GHCR_USERNAME='<github-username>'
export GHCR_TOKEN='<read-packages-token>'
sh scripts/linux/deploy.sh
unset GHCR_TOKEN
sh scripts/linux/status.sh
```

不要把真实 Token 直接写进可保存的脚本或 shell history。提供 `GHCR_TOKEN` 时，部署脚本使用临时 Docker 配置登录，并在退出时删除该临时配置。

六个服务必须全部 healthy。MySQL、RabbitMQ 和 Redis 不发布宿主机端口；应用、Prometheus 和 Grafana 只绑定 `127.0.0.1`。

排错时只读取指定服务的有限日志，不使用无限跟随：

```bash
sh scripts/linux/logs.sh app 100
```

### 4. SSH 隧道访问

客户端建立本地端口转发：

```bash
ssh \
  -L 8080:127.0.0.1:8080 \
  -L 9090:127.0.0.1:9090 \
  -L 3000:127.0.0.1:3000 \
  <authorized-host>
```

随后在客户端访问 `http://127.0.0.1:8080`、`9090` 和 `3000`。主机和云防火墙默认只批准 SSH，不直接开放 8080、9090、3000、3306、5672、6379 或 15672。

### 5. 关闭首次 Bootstrap

确认 ADMIN/REVIEWER 能登录后：

1. 将 `FINGUARD_AUTH_BOOTSTRAP_ENABLED` 改为 `false`；
2. 从 `.env.linux` 删除四项 Bootstrap 用户名/密码值；
3. 重新执行 `deploy.sh` 和 `status.sh`；
4. 确认登录仍成功，数据库只保存 BCrypt 哈希。

### 6. 安全停止、升级和回滚

保留卷的优雅停止：

```bash
sh scripts/linux/stop.sh
```

恢复或升级：把 `.env.linux` 的应用镜像改为另一个已验收完整 SHA，再执行：

```bash
sh scripts/linux/deploy.sh
sh scripts/linux/status.sh
```

回滚到脚本记录的上一个成功镜像：

```bash
sh scripts/linux/rollback.sh
sh scripts/linux/status.sh
```

也可把完整旧镜像作为 `rollback.sh` 的第一个参数。回滚只替换应用镜像，不回滚数据库；不得用 `docker compose down -v` 做停止、升级或回滚。

完整的权限、失败路径和验收矩阵见 [`docs/design/week6-day4-linux-deployment-design.md`](docs/design/week6-day4-linux-deployment-design.md)。

## 持续集成

`.github/workflows/ci.yml` 在 `push`、`pull_request` 和手动触发时执行：

1. 配置 Temurin Java 17 与 Maven 缓存；
2. 启动真实 MySQL、RabbitMQ 和 Redis，并等待健康；
3. 运行一次完整 `mvn clean package`；
4. 构建应用 Docker 镜像；
5. 检查镜像使用非 root 用户、Java 17 且不包含 Maven/源码。

该 workflow 的测试 job 负责 CI 验证；只有 `main` 的 `push` 在测试成功后进入独立发布 job，将完整 Git SHA 镜像推送到私有 GHCR。发布 job 会在隔离的 GitHub-hosted Ubuntu runner 上验证六服务部署、保留卷停机恢复以及可用前序 SHA 的回滚/再升级，并在结束时清理 runner 资源；它不会自动连接用户服务器，也不使用服务器生产凭据。

## 当前接口

### 认证

```text
POST   /api/auth/login
```

登录、健康检查、Prometheus 指标和 OpenAPI 文档允许匿名访问。其余业务接口必须携带合法的 Bearer Token。

连续失败登录按 remote address 与规范化用户名的摘要组合计数，默认第 6 次返回 `429 + Retry-After`；成功登录会清除当前组合的计数。Redis 故障时认证继续执行，但故障期间限流暂时失效。

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

上传在 ADMIN 授权后、文件读取前按 JWT userId 计数，默认第 11 次返回 `429 + Retry-After`；缺失、重复或无效文件也会消耗额度，匿名和 REVIEWER 请求不会消耗 ADMIN 配额。

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

### 审计日志

```text
GET    /api/audit-logs?page=1&size=20&actionCode=REVIEW_CONFIRMED&initiatedBy=1
```

审计查询仅允许 `ADMIN`，按 `created_at DESC, id DESC` 稳定分页。系统只记录 CSV 首次受理、导入失败、对账完成、审核确认和审核忽略；摘要由服务端固定模板生成，不包含 JWT、原始 CSV、文件名/hash、交易描述、完整审核说明、SQL、自由异常消息或堆栈。审计记录是应用层 append-only，不提供修改、删除或导出接口。

### 统计总览

```text
GET    /api/statistics/overview
```

统计总览允许 `ADMIN`、`REVIEWER` 查询，返回导入任务、对账任务/结果、风险命中和审核任务的分组计数及 `generatedAt`。MySQL 是统计真源；Redis 只保存固定 key 的 60 秒可重建 JSON 快照，缓存命中时 `generatedAt` 不变，相关业务成功提交后删除快照。Redis 不可用时接口直接回源 MySQL。

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

- 审核分页、详情、决策、乐观锁和五类关键操作审计已完成，但尚未实现审核撤销/重开、批量审核或审计导出；
- 导入和对账消费者采用单事务任务行锁，正常处理中间态不会独立提交，也不提供跨事务可见的处理租约；
- DLQ 目前依赖运维排查，尚未提供失败任务的人工重跑、覆盖导入或管理接口；
- 风险候选查询在 4,000 条合成数据上由 MySQL 优化器选择全表扫描；当时估算命中 15.89% 且数据量小，Day 3 未根据单次合成样本追加索引，留待 Week 6 用更真实数据规模压测后决定；
- 不提供复杂模糊匹配、金额容差或人工确认；Redis 仅用于一个全局统计快照和两类单实例固定窗口限流，不提供强一致缓存、分布式全局配额、账户锁定或动态规则。

## 当前范围

项目范围、学习方式和六周计划以 [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) 为唯一事实来源。后续每次只推进一个可运行、可测试、可提交的小里程碑。
