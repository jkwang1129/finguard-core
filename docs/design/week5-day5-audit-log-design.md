# Week 5 Day 5：关键业务操作审计日志任务安排

## 1. 里程碑结论

- **进入基线**：Week 5 Day 4 已在提交 `286391f` 完成；当前数据库最新迁移为 V9，共 13 张业务表，审核查询、REVIEWER 决策和数据库条件更新乐观锁均已落地。
- **测试基线**：进入本日时最近一次完整 `mvn clean test` 为 326/326；这只是历史基线，Day 5 完成后必须重新实跑并记录新数字。
- **本日目标**：新增 V10 `audit_logs` 作为五类关键业务动作的 MySQL 审计真源，让“业务事实”和“审计证据”在同一事务中一起提交或一起回滚，并提供仅 ADMIN 可访问的只读分页查询。
- **核心正确性**：审计写入必须加入现有业务事务，不使用 `REQUIRES_NEW` 旁路提交；业务回滚时不能留下假审计，审计失败时也不能留下已生效但无证据的业务状态。
- **完成定义**：五类动作唯一、安全、可查；重复上传、重复 MQ、ACK 丢失红投和重复审核没有重复审计；查询权限、分页、数据库约束、事务回滚和敏感信息屏蔽均有自动化与真实环境证据。

## 2. 为什么需要本日任务

Day 4 已经能回答“审核任务现在是什么状态”，但还不能完整回答：

- 谁发起了原始 CSV 上传；
- 某次异步失败属于哪个导入任务；
- 某次对账何时完整提交；
- 哪位 REVIEWER 最终确认或忽略了任务；
- 业务状态变化与审计证据是否属于同一次提交。

普通应用日志不能替代审计表：日志可能轮转、格式不稳定，也无法用数据库外键和唯一约束证明 actor、target 与业务对象真实存在。Day 5 只补齐最小审计闭环，不建设通用合规平台。

## 3. 当前事实与本日变更

### 3.1 当前已有

- `import_jobs.created_by` 保存上传发起用户；首次上传在同一事务内写入 job、原始文件和 Outbox，重复文件直接返回原任务。
- 异步导入消费者使用任务行锁、终态短路和手动 ACK；导入可进入 `SUCCESS / PARTIAL_SUCCESS / FAILED`。
- `reconciliation_jobs.created_by` 保存对账发起用户；对账结果、风险命中、审核任务和 job `COMPLETED` 已在同一事务提交。
- `review_tasks` 通过 `status='PENDING' AND version=?` 条件更新保证只有一个并发 REVIEWER 能完成决策。
- JWT subject 是可信用户 ID；审核人不能由请求体指定。

### 3.2 当前没有

- V10 或 `audit_logs` 表；
- `audit` Java 模块；
- 审计写入事务接入点；
- `/api/audit-logs` 查询接口；
- 审计权限、幂等、回滚与脱敏测试。

### 3.3 本日新增

- V10 追加迁移和数据库基线断言；
- 类型安全、仅追加的 audit 持久层；
- 五类白名单审计动作及固定安全摘要；
- 现有导入、对账、审核事务中的审计写入；
- 仅 ADMIN 可用的只读分页查询；
- 数据库、Service、HTTP/RBAC、重复投递、事务回滚和真实环境验收。

## 4. 范围边界

### 4.1 本日必须完成

- `CSV_UPLOAD_ACCEPTED`；
- `IMPORT_FAILED`；
- `RECONCILIATION_COMPLETED`；
- `REVIEW_CONFIRMED`；
- `REVIEW_IGNORED`；
- `GET /api/audit-logs`；
- V10 约束、索引、外键、唯一键与不可变写入边界；
- 同事务、重复执行、权限和敏感信息屏蔽证据。

### 4.2 本日明确不做

- 不审计普通 GET、健康检查、登录成功/失败或每一行 CSV 校验；
- 不使用通用 AOP 拦截所有 Controller/Service；
- 不提供审计新增、修改、删除、导出或历史回填 HTTP 接口；
- 不修改或删除 V1～V9，不给既有历史数据伪造审计记录；
- 不引入 Redis、统计、限流、前端、Elasticsearch、区块链、防篡改存储或外部 SIEM；
- 不把审计事件放进 RabbitMQ，也不新增 Outbox 事件类型；
- 不记录密码、JWT、原始 CSV、交易描述、完整审核 note、SQL、异常消息或堆栈。

## 5. 五类事件契约

| actionCode | 触发点 | actor | initiatedBy | target | outcome | 摘要 | 幂等边界 |
|---|---|---|---|---|---|---|---|
| `CSV_UPLOAD_ACCEPTED` | 首次 job、文件、Outbox 均写入后 | `USER`，上传者 | 上传者 | import job | `SUCCESS` | `CSV upload accepted` | 每个 import job 一次 |
| `IMPORT_FAILED` | import job 首次进入 `FAILED` | `SYSTEM` | `import_jobs.created_by` | import job | `FAILED` | `Import failed` | 每个 import job 一次 |
| `RECONCILIATION_COMPLETED` | results、risk hits、review tasks 已写入且 job 即将完成 | `SYSTEM` | `reconciliation_jobs.created_by` | reconciliation job | `SUCCESS` | 固定计数模板 | 每个 reconciliation job 一次 |
| `REVIEW_CONFIRMED` | REVIEWER 条件更新成功 | `USER`，reviewer | reviewer | review task | `SUCCESS` | `Review task confirmed` | 每个 task/动作一次 |
| `REVIEW_IGNORED` | REVIEWER 条件更新成功 | `USER`，reviewer | reviewer | review task | `SUCCESS` | `Review task ignored` | 每个 task/动作一次 |

这里的 `IMPORT_FAILED` 表示任务已合法提交为 `FAILED` 终态，不是“捕获任何 Java 异常后另开事务写日志”。若未知系统异常使原事务回滚，不能单独写一条看似已经生效的失败审计。

### 5.1 固定安全摘要

摘要只能由服务端白名单模板产生：

```text
CSV upload accepted
Import failed
Reconciliation completed: total=5, matched=2, unmatched=1, duplicate=1, suspicious=1
Review task confirmed
Review task ignored
```

对账计数来自准备提交的确定业务结果，只包含非敏感整数。导入失败摘要不拼接 `error_summary`、解析异常文本、文件名或被拒绝字段；审核摘要不拼接 decision note。

## 6. V10 `audit_logs` 设计

迁移名固定为：

```text
V10__create_audit_log_table.sql
```

### 6.1 字段

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT | 主键、自增 |
| `action_code` | VARCHAR(40) | 五类白名单动作 |
| `actor_type` | VARCHAR(16) | `USER / SYSTEM` |
| `actor_user_id` | BIGINT | USER 必填；SYSTEM 必须为空；FK users RESTRICT |
| `initiated_by` | BIGINT | 原业务请求发起用户；NOT NULL；FK users RESTRICT |
| `outcome` | VARCHAR(16) | `SUCCESS / FAILED` |
| `import_job_id` | BIGINT | 可空；FK import_jobs RESTRICT |
| `reconciliation_job_id` | BIGINT | 可空；FK reconciliation_jobs RESTRICT |
| `review_task_id` | BIGINT | 可空；FK review_tasks RESTRICT |
| `summary` | VARCHAR(255) | 固定模板安全摘要 |
| `created_at` | DATETIME(3) | NOT NULL，由 `businessClock` 显式写入 |

不增加 `updated_at`。本表只表达已经发生的事件，不存在后续修改语义。

### 6.2 CHECK 约束

- `action_code` 只能是五类白名单值；
- `actor_type` 只能是 `USER/SYSTEM`；
- `outcome` 只能是 `SUCCESS/FAILED`；
- USER 必须有 `actor_user_id` 且等于 `initiated_by`；SYSTEM 的 `actor_user_id` 必须为空；
- 三个 target 外键恰好一个非空；
- `CSV_UPLOAD_ACCEPTED/IMPORT_FAILED` 只能指向 import job；
- `RECONCILIATION_COMPLETED` 只能指向 reconciliation job；
- `REVIEW_CONFIRMED/REVIEW_IGNORED` 只能指向 review task；
- `IMPORT_FAILED` 必须配 `FAILED`，其余四类必须配 `SUCCESS`；
- `summary` trim 后长度必须在 1～255 之间。

CHECK 负责记录形状，外键负责引用真实性，Service 负责事件产生时机；三者不能互相替代。

### 6.3 唯一键与索引

- `uk_audit_logs_action_import(action_code, import_job_id)`；
- `uk_audit_logs_action_reconciliation(action_code, reconciliation_job_id)`；
- `uk_audit_logs_action_review(action_code, review_task_id)`；
- `idx_audit_logs_action_created_id(action_code, created_at DESC, id DESC)`；
- `idx_audit_logs_initiated_created_id(initiated_by, created_at DESC, id DESC)`。

MySQL 唯一索引允许多个 `NULL`，因此三个 action+target 唯一键只约束实际非空目标。默认无筛选分页是否需要额外索引，必须用代表性数据和真实 `EXPLAIN` 评估；本日不凭猜测追加索引。

### 6.4 “不可变”的项目含义

本项目中的不可变是应用层 append-only：

- `AuditLogMapper` 不继承暴露通用 update/delete 的 `BaseMapper`，只声明受限 insert 和分页 select；
- 不提供 UPDATE/DELETE SQL、Service 方法或 HTTP 路由；
- 外键全部 `ON DELETE RESTRICT`，业务对象和用户不能在保留审计时被物理删除。

这不是具备独立数据库账号、WORM 存储或外部签名的金融级防篡改系统，面试中不得夸大。

## 7. Java 模块与写入 API

建议文件：

```text
src/main/java/com/finguard/core/audit/controller/AuditLogController.java
src/main/java/com/finguard/core/audit/dto/AuditLogQueryRequest.java
src/main/java/com/finguard/core/audit/entity/AuditLog.java
src/main/java/com/finguard/core/audit/mapper/AuditLogMapper.java
src/main/java/com/finguard/core/audit/model/AuditActionCode.java
src/main/java/com/finguard/core/audit/model/AuditActorType.java
src/main/java/com/finguard/core/audit/model/AuditOutcome.java
src/main/java/com/finguard/core/audit/service/AuditLogService.java
src/main/java/com/finguard/core/audit/service/impl/AuditLogServiceImpl.java
src/main/java/com/finguard/core/audit/vo/AuditLogResponse.java
```

写入 API 使用类型安全的业务方法，不能让 Controller 或任意调用者传自由 action 字符串：

```text
recordCsvUploadAccepted(importJobId, initiatedBy)
recordImportFailed(importJobId, initiatedBy)
recordReconciliationCompleted(jobId, initiatedBy, counters)
recordReviewDecision(reviewTaskId, reviewerId, decision)
```

`recordReviewDecision` 只把现有 `ReviewDecision` 映射为两类 action。所有 ID 必须为正；计数必须非负且总数守恒；时间统一使用现有 `businessClock` 并截断到毫秒。

审计写方法使用 `@Transactional(propagation = MANDATORY)`，强制加入调用者已经开启的事务；不标注 `REQUIRES_NEW`。没有活动事务时直接失败，避免审计先于业务独立提交。只读分页方法单独使用 `@Transactional(readOnly = true)`。

## 8. 事务接入点

### 8.1 CSV 首次上传

```text
ImportJobTransactionService.createPending
  → INSERT import_jobs
  → INSERT import_job_files
  → INSERT outbox_events
  → INSERT audit_logs(CSV_UPLOAD_ACCEPTED)
  → COMMIT
```

- 插入审计失败时，job、文件和 Outbox 全部回滚；
- 相同文件 hash 再次上传时返回已有任务，不再次调用审计写入；
- 请求在 hash/持久化前失败时没有 job target，因此不写审计。

### 8.2 导入失败

所有“首次进入 FAILED”的现有路径都要接入同一审计方法：

- 文件结构/解析失败；
- 全部业务行失败导致任务终态为 FAILED；
- 消费处理异常转 `PROCESSING_FAILED`；
- 重试耗尽后转 FAILED。

状态更新和 `IMPORT_FAILED` 必须在同一事务。终态短路、重复 MQ 或 ACK 丢失红投不得再次写审计。若审计写入失败，状态更新和同事务内的行错误/交易写入一起回滚，后续仍可按现有恢复机制重试。

### 8.3 对账完成

```text
persist reconciliation_results
  → evaluate and persist risk_hits
  → generate review_tasks
  → update reconciliation job COMPLETED
  → INSERT audit_logs(RECONCILIATION_COMPLETED)
  → COMMIT
```

审计摘要计数必须与 job 即将提交的五个计数字段一致。任一写入失败时 results、hits、tasks、job COMPLETED 和 audit 全部回滚；重复 MQ 在终态短路，不产生第二条审计。

### 8.4 审核决策

```text
UPDATE review_tasks
WHERE id=? AND status='PENDING' AND version=?
  → 更新成功 1 行
  → INSERT audit_logs(REVIEW_CONFIRMED/REVIEW_IGNORED)
  → 回查响应
  → COMMIT
```

- 0 行更新时直接返回既有 404/409，不写审计；
- 审计失败或更新后回查失败时，条件更新一起回滚，任务恢复为 `PENDING(version=0)`；
- 两个并发决策仍只有一个成功事务，失败方没有审计；
- `actor_user_id` 和 `initiated_by` 都来自已验签 JWT subject，不读取请求体。

## 9. 查询接口与权限

唯一新增 HTTP 路由：

```http
GET /api/audit-logs?page=1&size=20&actionCode=REVIEW_CONFIRMED&initiatedBy=42
```

### 9.1 参数

- `page` 默认 1，必须大于 0；
- `size` 默认 20，范围 1～100；
- `actionCode` 可选，使用枚举绑定；
- `initiatedBy` 可选，必须为正整数；
- 排序固定为 `created_at DESC, id DESC`；
- 响应复用 `PageResponse<AuditLogResponse>`。

### 9.2 响应字段

```text
id, actionCode, actorType, actorUserId, initiatedBy, outcome,
importJobId, reconciliationJobId, reviewTaskId, summary, createdAt
```

不返回用户名、文件名、文件 hash、原始内容、交易描述、审核 note、JWT、SQL、异常信息或堆栈。

### 9.3 RBAC

| 身份 | GET `/api/audit-logs` |
|---|---|
| ADMIN | 200 |
| REVIEWER | 403 `ACCESS_DENIED` |
| 匿名/无效 JWT | 401 |
| 已认证但无已知角色 | 403 |

`SecurityConfiguration` 必须在 `.anyRequest().authenticated()` 前显式声明 ADMIN 规则，不能依赖“已认证即可”的兜底。

## 10. 失败与幂等语义

| 场景 | 预期 |
|---|---|
| 重复 CSV 文件 | 返回原 job；`CSV_UPLOAD_ACCEPTED` 仍为 1 条 |
| 重复导入 MQ / ACK 丢失 | 终态短路；失败审计最多 1 条 |
| 重复对账 MQ / ACK 丢失 | COMPLETED 短路；完成审计最多 1 条 |
| 同 version 并发审核 | 一个业务+审计提交，另一个 409 且无审计 |
| 重复提交终态审核 | 409；不增加审计 |
| 审计 insert 失败 | 当前业务事务全部回滚 |
| 非法 actor/target/outcome/summary | V10 CHECK 拒绝 |
| 未知 actor/initiatedBy/target | FK 拒绝 |
| 查询参数非法 | 400；数据库无写副作用 |
| REVIEWER/匿名查询 | 403/401；数据库无写副作用 |

不使用 `INSERT IGNORE` 吞掉约束错误。正常幂等先由既有 job 终态、文件唯一键和审核条件更新保证，V10 唯一键是最终防线；如果出现“业务状态未完成但对应审计已存在”的不一致，必须失败并暴露问题，不能静默当作成功。

## 11. 实施顺序

1. 先写 V10 数据库约束与失败测试，固定五类合法形状、非法 actor/target/outcome、唯一键和 FK 行为。
2. 新增 V10，并更新所有最新 Flyway 版本断言为 10、业务表数量为 14；在真实 MySQL 核对列、约束、外键和索引。
3. 建立 audit 枚举、实体、受限 Mapper、Service 和固定摘要；先完成持久层与查询单测/集成测试。
4. 接入首次 CSV 上传事务，证明 audit 失败会回滚 job、file、Outbox，重复文件不重复审计。
5. 接入导入四类 FAILED 路径，证明失败终态与 audit 同提交，重复消息与恢复流程不重复。
6. 接入对账完成事务，证明 results、risk hits、review tasks、job 和 audit 原子提交/回滚。
7. 接入审核决策事务，复用 Day 4 乐观锁并证明一个并发成功只对应一条审计。
8. 实现 ADMIN 只读分页接口和显式 RBAC，覆盖筛选、稳定排序、400/401/403 与零写副作用。
9. 运行聚焦测试、数据库基线、完整 `mvn clean test` 和真实 MySQL/RabbitMQ/JWT/HTTP 闭环。
10. 清理 audit-first 测试数据、消息、临时凭据和端口；执行敏感信息检查、Day 6 范围审计和 `git diff --check`，回填实际证据后再提交。

## 12. 文件计划

### 12.1 新增

```text
docs/design/week5-day5-audit-log-design.md
src/main/resources/db/migration/V10__create_audit_log_table.sql
src/main/java/com/finguard/core/audit/controller/AuditLogController.java
src/main/java/com/finguard/core/audit/dto/AuditLogQueryRequest.java
src/main/java/com/finguard/core/audit/entity/AuditLog.java
src/main/java/com/finguard/core/audit/mapper/AuditLogMapper.java
src/main/java/com/finguard/core/audit/model/AuditActionCode.java
src/main/java/com/finguard/core/audit/model/AuditActorType.java
src/main/java/com/finguard/core/audit/model/AuditOutcome.java
src/main/java/com/finguard/core/audit/service/AuditLogService.java
src/main/java/com/finguard/core/audit/service/impl/AuditLogServiceImpl.java
src/main/java/com/finguard/core/audit/vo/AuditLogResponse.java
src/test/java/com/finguard/core/audit/
```

### 12.2 修改候选

```text
TASKS.md
README.md                         # 仅在实现验收完成后更新能力与测试数字
src/main/java/com/finguard/core/importjob/service/impl/ImportJobTransactionService.java
src/main/java/com/finguard/core/reconciliation/service/impl/ReconciliationJobTransactionService.java
src/main/java/com/finguard/core/review/service/impl/ReviewTaskServiceImpl.java
src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java
src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java
src/test/java/com/finguard/core/auth/security/RbacAuthorizationIntegrationTest.java
现有 import/reconciliation/review 测试夹具与集成测试
```

不修改 V1～V9，不增加 Redis/Docker/依赖/MQ 拓扑。

## 13. 自动化测试矩阵

### 13.1 数据库与 Mapper

- 干净数据库 V1→V10，最新版本 10、业务表 14；
- 五类合法记录写入和分页回查；
- actor、initiatedBy、target、outcome、summary 的全部非法 CHECK 组合；
- 未知 user/import/reconciliation/review 外键失败；
- 三类 action+target 重复唯一冲突；
- 被审计对象和用户物理删除受到 RESTRICT；
- `EXPLAIN` 验证 action/initiatedBy 筛选使用预期索引；
- Mapper 没有 update/delete 能力。

### 13.2 业务事务

- 首次上传产生 1 条 accepted；重复文件仍为 1 条；审计失败时 job/file/outbox 全回滚；
- 解析失败、全行失败、处理异常、重试耗尽分别产生 1 条 failed；
- 重复导入消息和终态恢复不重复 failed；
- 对账完成生成 1 条计数一致的审计；审计失败时 results/hits/tasks/job COMPLETED 全回滚；
- confirm/ignore 分别产生正确 action；过期版本、终态重复和更新 0 行没有审计；
- 两个并发审核只有成功方产生 1 条审计；注入审计失败后任务回到 PENDING。

### 13.3 Controller 与安全

- ADMIN 默认分页、actionCode 筛选、initiatedBy 筛选和稳定排序；
- page/size/actionCode/initiatedBy 非法为 400；
- REVIEWER 为 403，匿名/无效 Token 为 401，无角色为 403；
- 拒绝请求不新增、修改或删除审计记录；
- 响应和数据库 summary 均无敏感字段或自由文本泄漏。

## 14. 真实验收

1. 确认 MySQL 8.4.10、RabbitMQ 4.3.4 均为 healthy。
2. 从干净测试库执行 V1→V10，核对表、CHECK、FK、唯一键、索引和 V1～V9 checksum 不变。
3. 使用真实 ADMIN JWT 首次上传成功 CSV，再重复上传，核对 accepted 审计只有 1 条。
4. 上传结构错误 CSV 并等待异步 FAILED，核对 system actor、原始发起人和安全摘要。
5. 完成一次真实异步对账，核对结果/风险/审核任务/job 计数和 completed 审计一致。
6. 使用两个 REVIEWER 分别完成 confirm 与 ignore 样例；再对同一任务发起双并发决策，核对一个 200、一个 409，且只有成功方审计。
7. ADMIN 查询返回 200；REVIEWER 查询 403；匿名查询 401；响应不含敏感数据。
8. 运行聚焦测试、数据库基线和完整 `mvn clean test`，记录实际 total/failures/errors/skipped。
9. 按 `audit_logs → review_tasks → risk_hits → reconciliation_results → reconciliation_jobs → transactions → import/outbox → accounts/users` 清理测试数据；清空 RabbitMQ ready/unacked，释放应用端口。
10. 执行敏感信息检查、范围审计和 `git diff --check`。

## 15. 验收标准

- 五类动作各自的 actor、initiatedBy、target、outcome、summary 和 createdAt 正确；
- 首次业务动作产生且最多产生一条审计，重复文件、重复 MQ、ACK 丢失红投和重复审核不重复；
- CSV 首次受理、导入失败、对账完成和审核决策均与对应审计同事务；任一审计写入失败时业务状态和副作用完整回滚；
- 审核并发仍保持一个成功、一个 409，且数据库只有成功方的一条终态审计；
- `/api/audit-logs` 分页与筛选稳定，只有 ADMIN 可访问；400/401/403 拒绝路径零写副作用；
- schema、FK、CHECK、唯一键、RESTRICT 和索引在真实 MySQL 中与设计一致；
- 不记录密码、JWT、原始 CSV、文件名/hash、交易描述、完整审核 note、SQL、异常消息或堆栈；
- 完整测试、真实 HTTP/MySQL/RabbitMQ 验收、数据/消息/端口清理、`git diff --check` 和范围审计通过；
- 变更中没有 Redis、统计、限流、新 MQ、通用 AOP 审计、历史回填或审计修改/删除接口。

## 16. 学习与面试重点

### 必须掌握

- 审计表为什么是 append-only 事实表，而不是普通业务日志；
- 为什么业务状态和审计必须同事务；
- actor 与 initiatedBy 的区别，尤其是异步消费者代表 SYSTEM 执行的场景；
- 幂等前置判断、数据库唯一键和重复消息终态短路各自负责什么；
- CHECK、FK、UNIQUE、RESTRICT 如何共同保护审计记录形状和引用。

### 边做边学

- 在既有事务中接入受限写入 Service；
- 审计失败注入与真实回滚验证；
- 稳定分页、枚举参数绑定、索引与 `EXPLAIN`；
- 安全摘要白名单和数据最小化。

### 暂不展开

- WORM/对象锁、哈希链、数字签名、审计库独立账号、CDC/SIEM；
- 通用 AOP 自动审计、跨服务分布式事务和事件溯源；
- Redis 缓存与限流，留到 Week 5 Day 6。

## 17. 常见错误预防

- 不要用 `REQUIRES_NEW` 让审计绕开业务事务；
- 不要在 catch 中记录“成功审计”后继续抛异常；
- 不要把普通运行日志、异常堆栈或自由格式请求体塞进 summary；
- 不要让 Controller 传入自由 actionCode、actor 或 target；
- 不要继承通用 Mapper 后又声称审计无法更新/删除；
- 不要用 `INSERT IGNORE` 把 CHECK、FK 或未知唯一冲突吞掉；
- 不要在重复 MQ 的终态短路后再次补写审计；
- 不要忘记全行校验失败、处理异常和重试耗尽等不同 FAILED 路径；
- 不要允许 REVIEWER 查询全量审计，也不要依赖 `.anyRequest().authenticated()`；
- 不要修改 V1～V9，V10 应用后只能通过新增补偿迁移演进。

## 18. 回滚

- 设计阶段只回退本文和 `TASKS.md` 对应计划，不影响任何运行时能力。
- 实现阶段的 Java、测试、权限和文档可按 Day 5 文件范围回退；回滚后 Day 4 风险/审核流程必须继续工作。
- V10 若已进入共享或需保留数据的数据库，不得修改或删除；需要调整只能新增 V11+ 补偿迁移。
- 不删除已有风险、审核、对账、导入、消息或用户数据来掩盖失败；测试数据必须 audit-first 按外键顺序清理。

## 19. 建议提交

```text
feat: add critical business audit logs
```
