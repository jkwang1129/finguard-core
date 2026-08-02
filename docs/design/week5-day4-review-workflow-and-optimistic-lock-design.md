# Week 5 Day 4：异常审核接口与乐观锁并发控制任务安排

## 1. 状态与本日结论

- **状态**：已实现并完成验收。
- **进入基线**：Week 5 Day 3 已在提交 `5128247` 完成；当前数据库最新迁移为 V9，共 13 张业务表，能够为三类对账异常和每条风险命中生成 `PENDING(version=0)` 审核任务。
- **回归基线**：进入 Day 4 前最近一次完整 `mvn clean test` 为 314/314；它只是 Day 3 的历史基线，Day 4 完成后必须重新实跑并记录新数字。
- **本日交付**：审核列表、详情、确认/忽略接口，数据库条件更新乐观锁，稳定的 404/409 分类，以及 ADMIN/REVIEWER 权限矩阵。
- **核心正确性**：MySQL 中 `review_tasks` 是审核状态真源；并发正确性由 `WHERE id=? AND status='PENDING' AND version=?` 的条件更新保证，不依赖 Java 进程锁或 Redis 锁。

## 2. 业务目标

Day 3 已能发现异常并生成待审核任务，但当前没有人能够通过 HTTP 查看或处理这些任务。Day 4 要补齐最小人工审核闭环：

```text
ADMIN / REVIEWER 查询审核列表或详情
  → 根据状态、来源、对账结果类型或风险规则筛选
  → REVIEWER 读取 PENDING(version=0) 任务
  → 携带期望 version 提交 CONFIRMED 或 IGNORED
  → 数据库条件更新审核状态、审核人、审核时间和说明
  → 更新 1 行：返回最新任务
  → 更新 0 行：分类为不存在、非法状态或版本冲突
```

业务含义保持克制：`CONFIRMED` 只表示审核人确认该异常或风险需要保留和后续关注，`IGNORED` 只表示本次审核判定可忽略；两者都不修改原始交易、对账结果或风险命中。

## 3. 范围边界

### 3.1 本日包含

- `GET /api/review-tasks` 分页筛选；
- `GET /api/review-tasks/{reviewTaskId}` 详情；
- `PATCH /api/review-tasks/{reviewTaskId}/decision` 确认或忽略；
- 查询投影、DTO、VO、Service、Mapper 条件更新和领域异常；
- `REVIEW_TASK_NOT_FOUND`、`INVALID_REVIEW_OPERATION`、`REVIEW_VERSION_CONFLICT` 三类稳定错误；
- ADMIN/REVIEWER 查询、仅 REVIEWER 决策的 Spring Security 规则；
- 单元、真实 MySQL、并发、MockMvc/RBAC 和真实 HTTP 验收。

### 3.2 本日不包含

- 不新增或修改 Flyway 迁移；V9 的字段、CHECK、外键、唯一键和分页索引已经满足本日需求；
- 不实现审核撤销、重开、转派、认领、批量审核、多级审批、超时升级或通用工作流引擎；
- 不使用 `synchronized`、本地锁、Redis 分布式锁或先 `SELECT ... FOR UPDATE` 再长时间持锁的悲观流程；
- 不修改或删除 `transactions`、`reconciliation_results`、`risk_hits`，不重新运行风险规则；
- 不实现 V10 审计日志。Day 4 先保留清晰的决策事务接入点，Day 5 再让审核终态和审计记录同事务提交；
- 不实现 Redis、统计、限流、新 RabbitMQ 消息、前端或 Week 6 监控/部署能力。

## 4. HTTP 契约

### 4.1 分页查询

```http
GET /api/review-tasks?page=1&size=20&status=PENDING&sourceType=RISK_HIT&ruleCode=LARGE_AMOUNT
```

| 参数 | 默认值与约束 |
|---|---|
| `page` | 默认 1，必须大于 0 |
| `size` | 默认 20，必须为 1～100 |
| `status` | 可选：`PENDING/CONFIRMED/IGNORED` |
| `sourceType` | 可选：`RECONCILIATION_EXCEPTION/RISK_HIT` |
| `resultType` | 可选：`UNMATCHED/DUPLICATE/SUSPICIOUS`，只用于异常来源 |
| `ruleCode` | 可选：`LARGE_AMOUNT/POSSIBLE_DUPLICATE/FREQUENT_TRANSACTION`，只用于风险来源 |

组合规则：

- `resultType` 与 `ruleCode` 不能同时出现；
- 携带 `resultType` 时，`sourceType` 必须省略或为 `RECONCILIATION_EXCEPTION`；
- 携带 `ruleCode` 时，`sourceType` 必须省略或为 `RISK_HIT`；
- 省略 `sourceType` 但携带来源专属条件时，由查询层推导对应来源；互相矛盾的组合返回 `400 INVALID_REQUEST`，不静默返回空页；
- 排序固定为 `review_tasks.created_at DESC, review_tasks.id DESC`，同一数据集重复翻页不因并列时间产生抖动。

响应复用现有 `PageResponse<T>`：

```json
{
  "page": 1,
  "size": 20,
  "total": 1,
  "pages": 1,
  "records": []
}
```

以上字段与现有 `PageResponse` 序列化契约一致；实施时用现有分页测试继续锁定，不能另造第二种分页包装。

### 4.2 详情查询

```http
GET /api/review-tasks/301
```

列表记录和详情使用同一最小响应形状：

```json
{
  "id": 301,
  "sourceType": "RISK_HIT",
  "reconciliationResultId": null,
  "riskHitId": 81,
  "csvTransactionId": 1009,
  "resultType": "MATCHED",
  "ruleCode": "LARGE_AMOUNT",
  "reasonCode": "AMOUNT_AT_OR_ABOVE_THRESHOLD",
  "status": "PENDING",
  "version": 0,
  "reviewedBy": null,
  "reviewedAt": null,
  "decisionNote": null,
  "createdAt": "2026-08-02T15:00:00",
  "updatedAt": "2026-08-02T15:00:00"
}
```

- 异常任务直接关联 `reconciliation_result_id`，`riskHitId/ruleCode/reasonCode` 为空；
- 风险任务直接关联 `risk_hit_id`，再经风险命中关联对账结果和 CSV 交易；其任务级 `reconciliationResultId` 保持为空，不伪造第二个来源外键；
- 查询只返回审核所需的 ID、分类、规则和状态，不返回原始 CSV、交易描述、JWT、SQL、异常堆栈或服务端路径。

### 4.3 决策接口

```http
PATCH /api/review-tasks/301/decision
Content-Type: application/json

{
  "decision": "CONFIRMED",
  "version": 0,
  "note": "Verified against source statement"
}
```

请求规则：

- `decision` 必填且只允许 `CONFIRMED/IGNORED`，使用独立 `ReviewDecision` 枚举，不能把 `PENDING` 暴露为可提交决策；
- `version` 必填且不能为负数；合法但过期的版本返回 409，不按 400 处理；
- `note` 可选，trim 后空白保存为 `null`，最多 255 个 Unicode 字符；不能仅依靠数据库截断；
- `reviewedBy` 只能从已验签 JWT 的 subject 取得，不能由请求体传入；
- `reviewedAt` 与显式 `updatedAt` 使用现有 `businessClock` 产生，便于测试且避免应用与数据库时间漂移；
- 成功返回 `200` 和更新后的任务，以便调用方取得终态、版本、审核人和审核时间。

## 5. 查询与持久层设计

### 5.1 查询投影

`ReviewTaskMapper` 增加一个详情查询和一个 MyBatis-Plus 分页查询。SQL 从 `review_tasks` 出发，根据来源使用 `LEFT JOIN`：

```text
review_tasks
  → reconciliation_results（异常来源直接关联）
  → risk_hits（风险来源直接关联）
      → reconciliation_results（取得 csvTransactionId/resultType）
```

V9 已有：

- `(status, created_at DESC, id DESC)`；
- `(source_type, status, created_at DESC, id DESC)`；
- 两个来源唯一键。

Day 4 先用真实 `EXPLAIN` 验证无筛选、status、sourceType+status 和来源专属过滤；只有证据表明现有索引明显不足时，才讨论追加 V10 之前的新迁移。不得修改 V9，也不得因一个小型合成样本猜测建索引。

### 5.2 条件更新

唯一允许的状态写 SQL 形状为：

```sql
UPDATE review_tasks
SET status = :decision,
    version = version + 1,
    reviewed_by = :reviewerId,
    reviewed_at = :reviewedAt,
    decision_note = :decisionNote,
    updated_at = :reviewedAt
WHERE id = :reviewTaskId
  AND status = 'PENDING'
  AND version = :expectedVersion;
```

更新成功必须恰好为 1 行；不能先普通 `UPDATE BY ID` 再在 Java 中判断，也不能让客户端提交整个实体覆盖数据库字段。V9 的 `chk_review_tasks_state` 继续作为最终形状防线：PENDING 必须是 version 0 且审核字段为空，终态必须是 version 1 且审核人/时间非空。

## 6. Service 流程与冲突分类

`ReviewTaskService.decide` 使用一个短事务：

```text
校验 taskId / reviewerId / decision / version / note
  → 按 ID 读取当前任务
  → 不存在：404 REVIEW_TASK_NOT_FOUND
  → 已是 CONFIRMED/IGNORED：409 INVALID_REVIEW_OPERATION
  → 当前 version 与 expectedVersion 不同：409 REVIEW_VERSION_CONFLICT
  → 执行条件 UPDATE
  → affectedRows = 1：回查并返回最新投影
  → affectedRows = 0：说明校验后发生竞争；回查当前状态并返回稳定 409
```

竞争失败的分类规则：

- 请求开始时已经看到终态：`INVALID_REVIEW_OPERATION`；
- 请求先看到合法 PENDING 同版本，但在条件更新时输给并发请求：`REVIEW_VERSION_CONFLICT`；
- 任务在诊断时不存在：`REVIEW_TASK_NOT_FOUND`；正常业务不提供删除接口，因此该分支主要保护异常数据变化。

两个并发 REVIEWER 都读取 version 0 时，数据库只允许一个更新 1 行。本日固定让竞争失败方返回 `REVIEW_VERSION_CONFLICT`，提示客户端刷新任务；失败方不能覆盖成功方的 `status/reviewed_by/reviewed_at/decision_note`。

查询方法使用 `@Transactional(readOnly = true)`；决策事务内不调用外部网络、RabbitMQ 或 Redis。Day 5 接入审计时，将在同一个决策事务中增加受限的审计写入，审计失败必须令条件更新一起回滚。

## 7. 错误与权限契约

| 场景 | HTTP | code |
|---|---:|---|
| 参数、枚举、note 或负 version 非法 | 400 | `VALIDATION_FAILED` / `INVALID_REQUEST` |
| 匿名或 Token 无效 | 401 | 现有认证错误码 |
| 角色不允许 | 403 | `ACCESS_DENIED` |
| 审核任务不存在 | 404 | `REVIEW_TASK_NOT_FOUND` |
| 任务已是终态 | 409 | `INVALID_REVIEW_OPERATION` |
| 请求版本过期或并发竞争失败 | 409 | `REVIEW_VERSION_CONFLICT` |
| 未分类异常 | 500 | `INTERNAL_SERVER_ERROR` |

权限矩阵：

| 接口 | 匿名 | ADMIN | REVIEWER | 无已知角色 |
|---|---:|---:|---:|---:|
| 列表 | 401 | 200 | 200 | 403 |
| 详情 | 401 | 200 | 200 | 403 |
| 决策 | 401 | 403 | 200 | 403 |

`SecurityConfiguration` 必须显式加入 GET 与 PATCH matcher；PATCH 规则只授予 REVIEWER。拒绝必须发生在 Controller/Service 写入前，并通过数据库前后快照证明无副作用。

## 8. 文件计划

```text
docs/design/week5-day4-review-workflow-and-optimistic-lock-design.md
src/main/java/com/finguard/core/review/controller/ReviewTaskController.java
src/main/java/com/finguard/core/review/dto/ReviewTaskQueryRequest.java
src/main/java/com/finguard/core/review/dto/ReviewDecisionRequest.java
src/main/java/com/finguard/core/review/exception/ReviewTaskNotFoundException.java
src/main/java/com/finguard/core/review/exception/InvalidReviewOperationException.java
src/main/java/com/finguard/core/review/exception/ReviewVersionConflictException.java
src/main/java/com/finguard/core/review/model/ReviewDecision.java
src/main/java/com/finguard/core/review/model/ReviewTaskView.java
src/main/java/com/finguard/core/review/service/ReviewTaskService.java
src/main/java/com/finguard/core/review/service/impl/ReviewTaskServiceImpl.java
src/main/java/com/finguard/core/review/vo/ReviewTaskResponse.java
src/main/java/com/finguard/core/review/mapper/ReviewTaskMapper.java
src/main/java/com/finguard/core/common/exception/ErrorCode.java
src/main/java/com/finguard/core/common/exception/GlobalExceptionHandler.java
src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java
src/test/java/com/finguard/core/review/
src/test/java/com/finguard/core/auth/security/RbacAuthorizationIntegrationTest.java
```

`ReviewTaskView` 是只读查询投影，不是新的业务真源；现有 `ReviewTask` Entity 继续承载表字段。若实施时 MyBatis 对 record 构造映射不稳定，可以改为包内只读 POJO，但不能把 JOIN 派生字段错误写进 Entity 并尝试持久化。

## 9. 执行顺序

1. 先写 DTO/错误/状态迁移和并发失败测试，锁定 400/404/409 与零副作用预期。
2. 增加查询投影和 Mapper SQL，用真实 MySQL 覆盖两种来源、全部筛选、稳定排序、空页和详情不存在。
3. 实现 `ReviewTaskService` 的只读查询、note 规范化、条件更新和冲突分类；注入固定 `Clock` 测试审核时间。
4. 实现 Controller 与三个 HTTP 路由，复用现有 `PageResponse` 和统一异常响应。
5. 更新 Spring Security，先完成匿名、无角色、ADMIN、REVIEWER 的路由矩阵测试，再做业务成功测试。
6. 使用两个真实事务和同步屏障并发提交同一任务，证明一个成功、一个 409，最终数据库只有成功方的决策。
7. 运行聚焦测试、数据库基线与完整回归；启动真实应用，用 ADMIN/REVIEWER JWT 完成 HTTP/MySQL 验收。
8. 清理审核、风险、对账、交易、账户和用户验收数据，确认消息和端口无残留，完成敏感信息检查、范围审计和 `git diff --check`。

## 10. 测试矩阵

### 10.1 DTO 与 Service 单元测试

- page/size 默认值与上下界；非法枚举、互斥筛选和来源推导；
- note 为 null、空串、全空白、255 字符、256 字符和多字节字符；
- CONFIRMED/IGNORED 成功，PENDING 决策被请求模型拒绝；
- 不存在、已确认、已忽略、过期 version、负 version；
- 固定 `Clock` 下 reviewedAt/updatedAt 和 trim 后 note 正确；
- 条件更新返回 0 时映射为并发版本冲突，且不返回伪成功对象。

### 10.2 真实 MySQL 集成测试

- 异常与风险两种来源详情投影正确；
- status/sourceType/resultType/ruleCode 单独与组合筛选正确；
- `created_at DESC, id DESC` 分页稳定，无重复、遗漏或 JOIN 放大计数；
- 成功决策满足 V9 CHECK：终态、version=1、reviewer/time 非空；
- 终态不能互转或重复提交；失败路径不修改任何审核字段；
- 两线程/两事务从同一 version=0 出发，恰好一个更新成功；
- 真实 `EXPLAIN` 记录现有 V9 索引的使用情况，不修改已应用迁移。

### 10.3 HTTP 与权限测试

- ADMIN/REVIEWER 查询均为 200；
- REVIEWER 决策 200，ADMIN 决策 403；
- 匿名 401，无已知角色 403；
- 无效参数 400、不存在 404、终态/版本冲突 409；
- 401/403/400/404/409 后数据库状态和计数不变；
- 真实 JWT 不能通过请求体伪造 reviewedBy。

## 11. 验收标准

- 三个接口与 Day 1 契约一致，列表/详情能解释两种来源且分页稳定；
- 条件更新 SQL 包含 id、PENDING 和 expected version，更新结果只能是 0 或 1；
- 真实 MySQL 并发实验恰好一个 200、一个 `409 REVIEW_VERSION_CONFLICT`，最终 version=1 且成功方字段未被覆盖；
- ADMIN/REVIEWER/匿名/无角色权限矩阵全部通过，所有拒绝路径零写入副作用；
- 不存在、终态、过期版本分别为稳定 404/409，统一错误响应不泄漏 SQL 或堆栈；
- 没有 V10、审计、Redis、限流、新 MQ、撤销/重开或通用工作流能力偷跑；
- Day 4 聚焦测试、数据库基线、完整 `mvn clean test`、真实 HTTP/MySQL 验收、清理与 `git diff --check` 全部通过，并回填实际数字而非复制 314/314。

## 12. 学习重点与常见错误

### 必须掌握

- 乐观锁不是“加一列 version”就完成，而是客户端携带旧版本、SQL 条件匹配版本、受影响行数参与业务判断；
- Java 前置查询只改善错误表达，真正防止覆盖的是单条原子条件更新；
- 401/403 由 Spring Security 过滤器链处理，404/409 由业务异常和全局异常处理器处理；
- 查询 JOIN 投影和可写 Entity 职责不同，分页 count 不能因 JOIN 产生重复行；
- 决策人必须来自已验签身份，不能相信请求体。

### 常见错误预防

- 不要使用 `selectById → 修改 Entity → updateById` 的无条件覆盖；
- 不要用 `synchronized` 或 Redis 锁掩盖多实例并发问题；
- 不要把重复提交当成幂等成功，否则调用方无法知道任务已由谁处理；
- 不要把 `PENDING` 放进决策请求枚举；
- 不要在 Controller 里拼 SQL、决定事务或吞掉更新数为 0；
- 不要让 ADMIN 因为“权限更高”自动获得 REVIEWER 专属决策能力；
- 不要修改 V9，若真实证据需要数据库演进只能追加新迁移；
- 不要在 Day 4 写假审计记录，Day 5 必须让审计与终态同事务。

## 13. 实际验收结果（2026-08-02）

- Day 4 审核、RBAC、数据库基线与统一异常聚焦测试 32/32；完整 `mvn clean test` 326/326，全部 0 failures、0 errors、0 skipped。
- 真实 MySQL 两事务同步竞争时，条件更新结果严格为 1 行和 0 行；真实双 HTTP 并发得到一个 200 和一个 `409 REVIEW_VERSION_CONFLICT`，最终只保存成功请求的终态、审核人、时间与说明。
- 真实进程在 18080 的健康检查为 `UP`；真实 ADMIN/REVIEWER 登录签发 JWT 后，列表/详情 200、匿名 401、ADMIN 决策 403、过期版本 409、REVIEWER 成功决策 200、终态重复提交 409。
- 注入“条件更新成功后响应回查失败”证明整个决策事务回滚，数据库恢复为 `PENDING|0|NULL|NULL|NULL`；非法 PENDING 决策、256 字符说明以及其他 400/401/403/404/409 路径均无写入副作用。
- status 分页的真实 `EXPLAIN` 使用 `idx_review_tasks_status_created_id`；source+status 的当前单行验收样本也由优化器选择该索引并追加 `Using where`。当前证据不足以证明新增索引有收益，因此没有修改 V9 或新增迁移。
- 验收数据、两名临时用户、临时凭据和应用进程已清理；8 个 RabbitMQ 业务队列 `ready=0/unacked=0`，18080 无监听，MySQL/RabbitMQ 均为 healthy。

## 14. 回滚与提交建议

Day 4 不新增迁移，回滚只涉及本日 Controller、DTO、VO、Service、Mapper 扩展、异常、权限规则、测试和本文。回滚后 V9 的 `PENDING` 审核任务仍保留，可在后续重新接入处理接口；不得删除已有风险、对账或交易数据来掩盖失败。

提交建议：

```text
feat: add optimistic exception review workflow
```
