# Week 5 Day 1：风险、审核、Redis 与审计契约设计

## 1. 状态与设计结论

- **状态**：设计完成，等待 Day 2 按边界实现。
- **本日性质**：纯设计里程碑，只新增本文并更新 `TASKS.md`。
- **当前事实**：仓库已完成 V1～V7、异步导入/对账、Outbox、手动 ACK、终态幂等、两级有限重试和独立 DLQ；尚未实现风险、审核、审计、统计或 Redis。
- **Week 5 主闭环**：对账完成后运行可解释风险规则，持久化风险命中，为对账异常和风险命中生成审核任务，REVIEWER 使用乐观锁确认或忽略，关键动作写入不可变审计日志；Redis 只承担统计缓存和登录/上传限流。
- **数据真源**：MySQL。Redis 故障不得改变导入、交易、对账、风险、审核或审计的最终正确性。
- **架构边界**：继续使用一个 Spring Boot 模块化单体，不引入 Drools、规则 DSL、工作流引擎、Redis 分布式锁、微服务或前端。

## 2. 业务目标、范围与非目标

### 2.1 本周业务目标

现有系统已经能把 CSV 交易稳定导入并输出 `MATCHED`、`UNMATCHED`、`DUPLICATE`、`SUSPICIOUS` 四类对账结果，但“发现异常之后谁处理、如何防止重复处理、如何追溯”仍为空白。Week 5 要补齐这段业务闭环：

```text
对账完成
  → 基于已持久化交易和对账结果执行风险规则
  → 保存可解释的风险命中
  → 为对账异常和风险命中创建待审核任务
  → REVIEWER 确认或忽略
  → 审核状态与审计日志原子提交
  → 统计接口展示导入、对账、风险和审核概况
```

Redis 增强两个外围场景：

```text
统计查询 → Redis 命中直接返回；未命中查 MySQL 并缓存
登录/上传 → Redis 原子固定窗口限流；超限返回 429
```

### 2.2 Day 1 包含

- 业务术语和模块依赖；
- 正常、重复、失败、并发和 Redis 故障流程；
- 三条风险规则的输入、阈值、原因码、边界和幂等；
- 审核任务生成矩阵、状态机、接口、权限和错误码；
- `risk_hits`、`review_tasks`、`audit_logs` 的候选表结构和迁移顺序；
- 五类审计事件的 actor、target、结果、事务和幂等语义；
- 统计响应、Redis key、TTL、失效和降级；
- 登录/上传限流维度、原子性、`Retry-After` 和降级；
- Day 2～Day 6 文件计划、测试矩阵和回滚边界。

### 2.3 Day 1 不包含

- Java 类、SQL 迁移、Maven 依赖、配置项或 Docker 服务；
- 对 V1～V7 的任何修改；
- 风险评分、机器学习、Drools、规则 DSL、规则版本平台或动态规则后台；
- 审核撤销、重开、转派、批量审核或多级审批；
- Redis Session、JWT 黑名单、分布式锁、业务最终幂等或复杂滑动窗口；
- 审计全文搜索、导出、修改、删除或历史数据回填；
- Week 6 的 Micrometer、Prometheus/Grafana、CI/CD、Linux 部署、压测或安全报告。

## 3. 当前实现事实与接入点

### 3.1 对账事务

`ReconciliationJobTransactionService.processPending` 当前先对 `reconciliation_jobs` 加行锁，再在同一事务中执行：

```text
PENDING / 可恢复 PROCESSING
  → 查询本次导入的 CSV_IMPORT 交易
  → 批量查询 MANUAL 候选
  → ReconciliationMatcher 生成决策
  → 批量插入 reconciliation_results
  → 更新 reconciliation_jobs 为 COMPLETED 及四类统计
```

因此 Day 3 的风险评估和审核任务生成应插入 `persistResults` 与最终 `complete` 之间，并继续处于同一事务。任何规则、风险持久化或审核任务生成异常都必须回滚本次对账结果和终态，由现有消费者重试链处理，不能产生“对账已完成但风险/审核缺失”的半成品。

当前对账结果使用批量 SQL 插入，不能假设每个 Java Entity 都会自动回填主键。Day 3 应在批量插入后按 `reconciliation_job_id` 重新查询结果，并以现有唯一关系 `(reconciliation_job_id, csv_transaction_id)` 与决策对齐；风险命中批量插入后同理按 `(reconciliation_result_id, rule_code)` 查询需要生成审核任务的 ID，不能依赖未验证的批量 generated keys。

### 3.2 导入事务

`ImportJobTransactionService` 当前把任务行锁、CSV 解析、交易/行错误写入和终态更新放在同一事务中；文件级失败、系统处理失败和重试耗尽有明确的 `FAILED` 路径。Day 5 审计接入时：

- 首次上传受理审计进入 `createPending` 的任务/文件/Outbox 事务；
- 文件级失败审计进入 `completeFileFailure` 事务；
- 系统处理失败和重试耗尽审计进入对应 `REQUIRES_NEW` 失败事务；
- 重复上传只返回已有任务，不重复写 `CSV_UPLOAD_ACCEPTED`。

### 3.3 认证与权限

当前 `/api/auth/login` 和 `/actuator/health` 匿名开放；账户、交易、导入和对账查询允许 `ADMIN`、`REVIEWER`，既有业务写操作仅允许 `ADMIN`。Week 5 新增权限后仍由 Spring Security 在 Controller 前负责 `401/403`：

- 审核查询：`ADMIN`、`REVIEWER`；
- 审核决策：仅 `REVIEWER`；
- 审计查询：仅 `ADMIN`；
- 统计查询：`ADMIN`、`REVIEWER`；
- 登录限流：匿名登录入口内部执行；
- 上传限流：必须先通过 ADMIN 认证和授权，再消耗配额。

## 4. 术语与模块边界

| 术语 | 本项目含义 | 不代表 |
|---|---|---|
| 对账异常 | `UNMATCHED`、`DUPLICATE`、`SUSPICIOUS` 对账结果 | 已确认欺诈 |
| 风险命中 | 一条确定性规则对一个已对账 CSV 交易输出的可解释结果 | 最终审核结论或概率分数 |
| 审核任务 | 对一个对账异常或一个风险命中的人工决策工作项 | 修改原交易或重跑对账 |
| CONFIRMED | REVIEWER 确认该异常/风险需要保留和后续处理 | 自动冻结账户或删除交易 |
| IGNORED | REVIEWER 判断该异常/风险可忽略 | 删除风险命中、对账结果或审计证据 |
| 审计日志 | 五类关键业务动作的不可变追踪记录 | 应用调试日志或每次查询日志 |
| 统计快照 | 某一时刻从 MySQL 聚合出的全局数量 | 实时强一致监控指标 |
| 限流窗口 | Redis 维护的一段固定计数周期 | 业务唯一键或最终幂等 |

模块依赖保持单向：

```text
reconciliation
  → risk（规则评估与风险命中）
  → review（根据结果/命中生成任务）
  → audit（Day 5 写对账完成审计）

review → audit（审核决策与审计同事务）
importjob → audit（上传/失败审计）
statistics → 只读查询 importjob/reconciliation/risk/review
auth/importjob controller → ratelimit
statistics/ratelimit → Redis 基础组件
```

`risk` 和 `review` 不反向调用 `reconciliation` Service；只读取明确的数据模型/Mapper 输入，避免循环依赖。`audit` 不驱动业务，业务模块调用受限的审计写接口。

## 5. 目标流程与失败决策

### 5.1 正常对账、风险和审核生成

```text
RabbitMQ 对账消息
  → 对 reconciliation_jobs 行加锁
  → 生成并批量保存 reconciliation_results
  → 批量加载风险规则所需候选数据
  → 对每个本次 CSV 交易运行启用规则
  → 批量保存 risk_hits（唯一键兜底）
  → 为三类对账异常生成异常审核任务
  → 为每个风险命中生成风险审核任务
  → 将 reconciliation_jobs 更新为 COMPLETED
  → 事务提交
  → Listener 手动 ACK
```

Day 5 加入审计后，`RECONCILIATION_COMPLETED` 审计也在上述事务中、最终提交前写入。

### 5.2 无异常、无风险

`MATCHED` 且三条规则都不命中时，只保存原对账结果，不创建 `risk_hits` 或 `review_tasks`。这不是失败，也不能创建空任务或零值风险记录。

### 5.3 重复或并发评估

- 同一对账消息红投：终态短路，不重新执行规则；
- 同一事务内重复产生风险候选：`uk_risk_hits_result_rule` 兜底；
- 同一对账异常重复建任务：`uk_review_tasks_reconciliation_result` 兜底；
- 同一风险命中重复建任务：`uk_review_tasks_risk_hit` 兜底；
- 唯一键冲突只能识别预期约束；其他完整性错误继续失败并回滚，禁止 `INSERT IGNORE`。

### 5.4 规则或持久化异常

规则代码抛出系统异常、批量查询失败、风险命中写入数量不一致、审核任务写入失败时：

```text
当前数据库事务整体回滚
  → reconciliation_results 不新增
  → risk_hits 不新增
  → review_tasks 不新增
  → reconciliation_jobs 不进入 COMPLETED
  → Listener 不 ACK
  → 进入现有有限 retry / DLQ 流程
```

禁止捕获异常后继续把对账任务标为 `COMPLETED`。

### 5.5 并发审核

两个 REVIEWER 都读取 `version=0`，随后分别提交：

```text
UPDATE review_tasks
SET status=?, version=version+1, reviewed_by=?, reviewed_at=?, decision_note=?
WHERE id=? AND status='PENDING' AND version=0
```

第一个更新 1 行并与审计一起提交；第二个更新 0 行，重新查询后返回 `409 REVIEW_VERSION_CONFLICT` 或 `409 INVALID_REVIEW_OPERATION`，不得覆盖第一人的状态、说明、审核人或时间。

### 5.6 审计写入失败

审计属于 P0 关键证据，不采用“业务成功、审计尽力而为”。业务状态和对应审计必须同事务：

- CSV 首次受理与 `CSV_UPLOAD_ACCEPTED` 一起提交；
- 导入失败终态与 `IMPORT_FAILED` 一起提交；
- 对账完成与 `RECONCILIATION_COMPLETED` 一起提交；
- 审核终态与 `REVIEW_CONFIRMED/REVIEW_IGNORED` 一起提交。

审计插入失败时业务事务回滚；MQ 驱动的业务由现有重试链恢复，HTTP 审核返回统一 `500` 且任务保持 `PENDING`。

### 5.7 Redis 缓存未命中或失效

```text
GET /api/statistics/overview
  → GET finguard:statistics:overview:v1
  → 命中：反序列化后返回
  → 未命中：查询 MySQL → 生成快照 → SET TTL 60s → 返回
```

业务写事务只在提交成功后失效统计 key；回滚不得提前删除或写入新缓存。

### 5.8 Redis 不可用

- 统计缓存：记录不含敏感数据的 WARN，直接查询 MySQL 并返回，不尝试把 Redis 当业务错误；
- 登录/上传限流：本项目选择 **fail-open**，记录限流暂不可用并继续现有流程；
- 业务幂等：仍由 SHA-256、数据库唯一键、任务行锁、终态短路和乐观锁保证；
- 限制：Redis 故障期间反暴力破解和上传频控会临时失效，Week 6 指标/告警应暴露该降级状态。

选择 fail-open 是因为 Redis 在本项目被定义为辅助缓存，不应让单个缓存故障阻断登录和核心导入；这是可用性优先的明确取舍，不宣称没有安全代价。

## 6. 风险规则契约

### 6.1 公共输入与输出

每条规则实现统一 `RiskRule`，由 Spring 按稳定顺序注入：

```text
RiskRule
  ruleCode()
  enabled()
  evaluate(RiskEvaluationContext) → Optional<RiskRuleResult>
```

`RiskEvaluationContext` 至少包含：

- `reconciliationJobId`；
- `reconciliationResultId`；
- 当前 `CSV_IMPORT` 交易；
- 当前对账结果类型和原因码；
- 按批次预加载的同账户历史 CSV 交易候选；
- 固定业务时区和只读规则配置。

`RiskRuleResult` 至少包含：

- `ruleCode`、`reasonCode`；
- 对应的观测金额或次数；
- 阈值快照；
- 时间窗口秒数；
- 由服务端生成的固定安全摘要。

规则只返回命中结果，不直接写数据库、不创建审核任务、不修改交易或对账结果。

### 6.2 执行顺序

稳定顺序为：

```text
LARGE_AMOUNT
POSSIBLE_DUPLICATE
FREQUENT_TRANSACTION
```

顺序只用于稳定测试和输出，不表示风险优先级或冲突覆盖。一个交易可以同时命中多条规则，每条命中独立持久化。

### 6.3 LargeAmountRule

| 项目 | 决策 |
|---|---|
| ruleCode | `LARGE_AMOUNT` |
| reasonCode | `AMOUNT_AT_OR_ABOVE_THRESHOLD` |
| 默认启用 | `true` |
| 默认阈值 | `10000.00` CNY |
| 比较 | `amount.compareTo(threshold) >= 0` |
| 输入范围 | 本次对账的全部未删除 `CSV_IMPORT` 交易，不区分方向 |
| 快照 | `observed_amount`、`threshold_amount` |

边界样例：

| 金额 | 阈值 | 结果 |
|---:|---:|---|
| `9999.99` | `10000.00` | 不命中 |
| `10000.00` | `10000.00` | 命中 |
| `10000.01` | `10000.00` | 命中 |

阈值必须为正、scale 不超过 2，启动时校验；禁止用 `double`。

### 6.4 DuplicateTransactionRule

此规则检测“外部流水号不同，但疑似重复支付”的近时同额交易，与三种已有重复保护严格区分：

| 机制 | 判断对象 | 职责 |
|---|---|---|
| 交易唯一键 | 同账户、同来源、同外部流水号 | 阻止相同业务键重复入库 |
| CSV 行重复校验 | 同一文件/数据库已有业务键 | 数据质量错误，不创建交易 |
| 对账 `DUPLICATE` | 一个 CSV 交易对应多个/已占用 MANUAL 候选 | 表示对账匹配歧义 |
| `POSSIBLE_DUPLICATE` 风险规则 | 同账户、同方向、同金额、近时但不同交易 | 提示可能重复支付，不删除数据 |

| 项目 | 决策 |
|---|---|
| ruleCode | `POSSIBLE_DUPLICATE` |
| reasonCode | `SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME` |
| 默认启用 | `true` |
| 默认窗口 | 前 5 分钟，含边界 |
| 候选 | 未删除 `CSV_IMPORT`，同账户、同方向、精确金额、不同 ID，排序早于当前交易 |
| 命中 | 至少 1 条更早候选 |
| 快照 | `observed_count`、`threshold_count=1`、`window_seconds=300` |

只把排序较后的交易标记为命中，排序键为 `(transaction_time, id)`，从而同一对交易不会互相产生镜像命中。候选查询按账户和时间范围批量完成，禁止每笔交易单独 SQL。

边界样例：

- 同账户、同方向、同金额，恰好相差 5 分钟：命中后发生的交易；
- 相差 5 分 1 毫秒：不命中；
- 金额、方向或账户任一不同：不命中；
- 外部流水号相同：应已被数据库唯一键拦截，不靠风险规则处理。

### 6.5 FrequentTransactionRule

| 项目 | 决策 |
|---|---|
| ruleCode | `FREQUENT_TRANSACTION` |
| reasonCode | `EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD` |
| 默认启用 | `true` |
| 默认方向 | `EXPENSE` |
| 默认窗口 | `[当前时间-10分钟, 当前时间]`，含两端 |
| 默认阈值 | 同账户至少 5 笔 |
| 候选 | 未删除 `CSV_IMPORT` 支出交易，可跨导入任务 |
| 快照 | `observed_count`、`threshold_count=5`、`window_seconds=600` |

只评估本次对账中的交易，但历史候选可以来自先前已导入交易。第五笔及之后满足窗口的本次交易命中；收入不计数。查询按本批账户和总体时间上下界加载，再在内存中按 `(account_id, transaction_time, id)` 滑动窗口计算，避免 N+1。

当多笔交易具有相同 `transaction_time` 时，只统计排序键小于等于当前 `(transaction_time, id)` 的交易，避免较大 ID 的“未来同刻交易”让较小 ID 提前命中。

边界样例：

- 10 分钟内第 4 笔支出：不命中；
- 10 分钟内第 5 笔支出：命中；
- 最早一笔恰好在 `t-10分钟`：计入；
- 同窗口但不同账户或收入方向：不计入。

### 6.6 规则配置

Day 3 计划使用类型安全配置，候选前缀：

```yaml
finguard:
  risk:
    large-amount:
      enabled: true
      threshold: 10000.00
    possible-duplicate:
      enabled: true
      window: 5m
    frequent-transaction:
      enabled: true
      direction: EXPENSE
      window: 10m
      threshold-count: 5
```

配置只支持启停和简单阈值；不从 Redis 或数据库动态加载，不做热更新和版本平台。风险命中保存实际阈值快照，避免后续配置变化后无法解释历史结果。

## 7. 审核任务契约

### 7.1 生成矩阵

| 来源 | 是否创建任务 | sourceType | 幂等依据 |
|---|---|---|---|
| `MATCHED` 且无风险 | 否 | — | — |
| `UNMATCHED` | 是 | `RECONCILIATION_EXCEPTION` | `reconciliation_result_id` 唯一 |
| `DUPLICATE` | 是 | `RECONCILIATION_EXCEPTION` | `reconciliation_result_id` 唯一 |
| `SUSPICIOUS` | 是 | `RECONCILIATION_EXCEPTION` | `reconciliation_result_id` 唯一 |
| 任一 `risk_hit` | 是 | `RISK_HIT` | `risk_hit_id` 唯一 |

同一个对账结果可以有一个对账异常任务和多个风险任务，因为它们表达不同事实；同一来源不能生成两次任务。

### 7.2 状态机

```text
PENDING(version=0)
  ├─ CONFIRMED(version=1)
  └─ IGNORED(version=1)
```

规则：

- 只允许一次终态转换；
- 终态不能互转、撤销或重开；
- 请求必须携带读取到的 `version`；
- `PENDING` 时 `reviewed_by/reviewed_at` 为空；
- 终态时 `reviewed_by/reviewed_at` 非空；
- `decision_note` 可选，trim 后最多 255 字符；空白按 `null` 保存；
- 审核只改变 `review_tasks`，不修改 `transactions`、`reconciliation_results` 或 `risk_hits`。

### 7.3 API

```text
GET   /api/review-tasks
GET   /api/review-tasks/{reviewTaskId}
PATCH /api/review-tasks/{reviewTaskId}/decision
```

列表参数：

| 参数 | 类型 | 默认/限制 |
|---|---|---|
| `page` | long | 默认 1，必须 > 0 |
| `size` | long | 默认 20，1～100 |
| `status` | enum | 可选 `PENDING/CONFIRMED/IGNORED` |
| `sourceType` | enum | 可选 `RECONCILIATION_EXCEPTION/RISK_HIT` |
| `resultType` | enum | 仅异常来源筛选，可选三类异常 |
| `ruleCode` | enum | 仅风险来源筛选，可选三条规则 |

稳定排序为 `created_at DESC, id DESC`。

决策请求：

```json
{
  "decision": "CONFIRMED",
  "version": 0,
  "note": "Verified against source statement"
}
```

成功返回 `200` 和更新后的任务；不使用 `204`，便于调用方取得新版本和审计时间。

详情与列表记录的最小响应字段：

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

异常任务的 `reconciliationResultId`、`resultType` 非空，`riskHitId`、`ruleCode`、`reasonCode` 为空；风险任务则持有 `riskHitId`，并可通过风险命中关联展示对账结果和 CSV 交易。Week 5 不提供独立风险写接口，也不扩展为通用风险管理 API。

### 7.4 权限矩阵

| 接口 | 匿名 | ADMIN | REVIEWER | 无已知角色 |
|---|---:|---:|---:|---:|
| 查询列表 | 401 | 200 | 200 | 403 |
| 查询详情 | 401 | 200 | 200 | 403 |
| 提交确认/忽略 | 401 | 403 | 200 | 403 |

拒绝请求必须发生在 Controller/Service 写逻辑之前，并用数据库计数和状态证明无副作用。

### 7.5 错误码

| 场景 | HTTP | code |
|---|---:|---|
| 参数、枚举、note、version 非法 | 400 | `VALIDATION_FAILED` / `INVALID_REQUEST` |
| 无身份或 Token 无效 | 401 | 现有认证错误码 |
| 角色不允许 | 403 | `ACCESS_DENIED` |
| 审核任务不存在 | 404 | `REVIEW_TASK_NOT_FOUND` |
| 任务已是终态 | 409 | `INVALID_REVIEW_OPERATION` |
| 请求 version 过期 | 409 | `REVIEW_VERSION_CONFLICT` |
| 限流超限 | 429 | `RATE_LIMIT_EXCEEDED` |
| 未分类系统异常 | 500 | `INTERNAL_SERVER_ERROR` |

版本冲突与已终态的区分方式：条件更新 0 行后按 ID 查询；不存在返回 404，状态非 `PENDING` 返回 `INVALID_REVIEW_OPERATION`，状态仍为 `PENDING` 但版本不同返回 `REVIEW_VERSION_CONFLICT`。

## 8. 数据模型与迁移设计

### 8.1 ER 关系

```text
reconciliation_jobs
  1 ── * reconciliation_results
             1 ── * risk_hits
             1 ── 0..1 review_tasks.reconciliation_result_id

risk_hits
  1 ── 0..1 review_tasks.risk_hit_id

users
  1 ── * review_tasks.reviewed_by
  1 ── * audit_logs.actor_user_id / initiated_by

import_jobs ----------- 0..* audit_logs.import_job_id
reconciliation_jobs -- 0..* audit_logs.reconciliation_job_id
review_tasks --------- 0..* audit_logs.review_task_id
```

审核来源使用两个真实可空外键，并用 CHECK 保证恰好一个非空；不采用无法建立引用完整性的通用 `source_type + source_id`。审计目标也使用三个真实可空外键和恰好一个目标约束。

### 8.2 V8 `risk_hits`

候选迁移名：`V8__create_risk_hit_table.sql`。

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT | PK，自增 |
| `reconciliation_result_id` | BIGINT | NOT NULL，FK → `reconciliation_results.id`，RESTRICT |
| `rule_code` | VARCHAR(32) | 三条规则之一 |
| `reason_code` | VARCHAR(64) | 与规则匹配的稳定原因码 |
| `observed_amount` | DECIMAL(19,2) | 大额规则使用，其余为空 |
| `threshold_amount` | DECIMAL(19,2) | 大额阈值快照，其余为空 |
| `observed_count` | INT UNSIGNED | 重复/高频规则使用 |
| `threshold_count` | INT UNSIGNED | 重复/高频阈值快照 |
| `window_seconds` | INT UNSIGNED | 重复/高频窗口，大额为空 |
| `reason_summary` | VARCHAR(255) | 服务端固定安全摘要 |
| `created_at` | DATETIME(3) | NOT NULL，默认当前时间 |

约束与索引：

- `uk_risk_hits_result_rule(reconciliation_result_id, rule_code)`：规则幂等真源；
- `idx_risk_hits_rule_created_id(rule_code, created_at DESC, id DESC)`：规则筛选和统计；
- `chk_risk_hits_rule/reason/shape`：规则、原因码和金额/次数字段组合必须匹配；
- 金额、次数、阈值和窗口若非空必须为正；
- 不存配置 JSON、异常堆栈、交易描述或原始 CSV。

选择 `reconciliation_result_id + rule_code`，而不是重复保存 job/transaction 外键，因为结果行已经唯一映射本次 job 和 CSV 交易，能减少三列关系不一致的风险。

### 8.3 V9 `review_tasks`

候选迁移名：`V9__create_review_task_table.sql`。

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT | PK，自增 |
| `source_type` | VARCHAR(32) | `RECONCILIATION_EXCEPTION/RISK_HIT` |
| `reconciliation_result_id` | BIGINT | 可空 FK，RESTRICT |
| `risk_hit_id` | BIGINT | 可空 FK，RESTRICT |
| `status` | VARCHAR(16) | `PENDING/CONFIRMED/IGNORED` |
| `version` | INT UNSIGNED | PENDING=0，终态=1 |
| `reviewed_by` | BIGINT | 可空 FK → users，RESTRICT |
| `reviewed_at` | DATETIME(3) | 可空 |
| `decision_note` | VARCHAR(255) | 可空、安全文本 |
| `created_at` | DATETIME(3) | NOT NULL |
| `updated_at` | DATETIME(3) | NOT NULL，自动更新 |

约束与索引：

- `uk_review_tasks_reconciliation_result(reconciliation_result_id)`；
- `uk_review_tasks_risk_hit(risk_hit_id)`；
- `chk_review_tasks_source`：sourceType 与恰好一个来源外键严格匹配；
- `chk_review_tasks_state`：PENDING 与终态字段/版本形状严格匹配；
- `idx_review_tasks_status_created_id(status, created_at DESC, id DESC)`；
- `idx_review_tasks_source_status(source_type, status, created_at DESC, id DESC)`。

MySQL 唯一索引允许多个 `NULL`，因此两个来源各自的唯一索引可以保证非空来源只出现一次。

### 8.4 V10 `audit_logs`

候选迁移名：`V10__create_audit_log_table.sql`。

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT | PK，自增 |
| `action_code` | VARCHAR(40) | 五类白名单动作 |
| `actor_type` | VARCHAR(16) | `USER/SYSTEM` |
| `actor_user_id` | BIGINT | USER 必填，SYSTEM 必须为空，FK RESTRICT |
| `initiated_by` | BIGINT | 原请求发起用户，NOT NULL，FK RESTRICT |
| `outcome` | VARCHAR(16) | `SUCCESS/FAILED` |
| `import_job_id` | BIGINT | 可空 FK，RESTRICT |
| `reconciliation_job_id` | BIGINT | 可空 FK，RESTRICT |
| `review_task_id` | BIGINT | 可空 FK，RESTRICT |
| `summary` | VARCHAR(255) | 固定模板安全摘要 |
| `created_at` | DATETIME(3) | NOT NULL |

约束与索引：

- 三个目标外键恰好一个非空，且与 actionCode 匹配；
- `CSV_UPLOAD_ACCEPTED/IMPORT_FAILED` 只指向 import job；
- `RECONCILIATION_COMPLETED` 只指向 reconciliation job；
- `REVIEW_CONFIRMED/REVIEW_IGNORED` 只指向 review task；
- USER actor 必须有 `actor_user_id`，SYSTEM actor 必须为空；
- 三个唯一索引 `(action_code, import_job_id)`、`(action_code, reconciliation_job_id)`、`(action_code, review_task_id)` 防止重复动作证据；
- 查询索引 `(action_code, created_at DESC, id DESC)` 和 `(initiated_by, created_at DESC, id DESC)`；
- 不提供 UPDATE/DELETE Mapper 或写接口。

### 8.5 迁移纪律

- V1～V7 永不修改；
- Day 2 只新增 V8，Day 3 新增 V9，Day 5 新增 V10；
- 每个迁移日同步更新 `DatabaseBaselineIntegrationTest` 的最新版本、表数量、外键、唯一键和 CHECK 断言；
- 在真实 MySQL 上核对列、索引、外键和约束；没有真实查询依据不额外加索引；
- 所有外键使用 `ON DELETE RESTRICT`，保留风险、审核和审计证据。

## 9. 审计事件契约

| actionCode | 触发点 | actor | initiatedBy | target | outcome | 幂等 |
|---|---|---|---|---|---|---|
| `CSV_UPLOAD_ACCEPTED` | 首次任务/文件/Outbox 创建 | USER=上传者 | 上传者 | import job | SUCCESS | 每任务一次 |
| `IMPORT_FAILED` | 导入进入 FAILED | SYSTEM | import.createdBy | import job | FAILED | 每任务一次 |
| `RECONCILIATION_COMPLETED` | 结果、风险、审核任务均成功且即将完成 | SYSTEM | reconciliation.createdBy | reconciliation job | SUCCESS | 每任务一次 |
| `REVIEW_CONFIRMED` | REVIEWER 条件更新成功 | USER=reviewer | reviewer | review task | SUCCESS | 每任务终态一次 |
| `REVIEW_IGNORED` | REVIEWER 条件更新成功 | USER=reviewer | reviewer | review task | SUCCESS | 每任务终态一次 |

安全摘要使用固定服务端模板，例如：

```text
CSV upload accepted
Import failed: PROCESSING_FAILED
Reconciliation completed: total=5, matched=2, unmatched=1, duplicate=1, suspicious=1
Review task confirmed
Review task ignored
```

摘要不得包含用户名、文件原始内容、JWT、密码、SQL、堆栈、自由格式异常消息或完整审核 note。审计查询只允许 ADMIN：

```text
GET /api/audit-logs?page=1&size=20&actionCode=...&initiatedBy=...
```

稳定排序 `created_at DESC, id DESC`，不提供单条修改、删除和导出接口。

## 10. 统计接口与 Redis 缓存

### 10.1 HTTP 契约

```text
GET /api/statistics/overview
```

允许 `ADMIN`、`REVIEWER`，匿名 401、无已知角色 403。最小响应：

```json
{
  "generatedAt": "2026-08-02T15:00:00+08:00",
  "importJobs": {
    "total": 10,
    "pending": 1,
    "processing": 1,
    "success": 5,
    "partialSuccess": 2,
    "failed": 1
  },
  "reconciliationJobs": {
    "total": 8,
    "pending": 1,
    "processing": 0,
    "completed": 6,
    "failed": 1
  },
  "reconciliationResults": {
    "matched": 20,
    "unmatched": 3,
    "duplicate": 2,
    "suspicious": 1
  },
  "riskHits": {
    "total": 5,
    "largeAmount": 2,
    "possibleDuplicate": 1,
    "frequentTransaction": 2
  },
  "reviewTasks": {
    "pending": 4,
    "confirmed": 1,
    "ignored": 1
  }
}
```

所有统计来自 MySQL `COUNT/GROUP BY`；`generatedAt` 是生成数据库快照的业务时区时间，缓存命中时保持原值，便于调用方理解数据新鲜度。

### 10.2 Key、序列化与 TTL

| 项目 | 决策 |
|---|---|
| key | `finguard:statistics:overview:v1` |
| value | JSON，显式 DTO，不使用 Java 原生序列化 |
| TTL | 60 秒 |
| 空值 | 不缓存 |
| DB 异常 | 不写缓存，按现有 500 处理 |
| Redis 异常 | 回退 MySQL，返回正常统计 |

版本号进入 key，响应结构不兼容变化时使用 `v2`，不反序列化旧 Java 类型。

### 10.3 失效时机

以下事务提交成功后删除统计 key：

- 导入任务首次创建或终态变化；
- 对账任务首次创建、完成或失败；
- 风险命中批量插入；
- 审核任务批量生成；
- 审核状态成功转换。

使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 或等价事务同步；禁止在事务提交前删除。Redis 删除失败只记录 WARN，TTL 最终会纠正，不回滚已提交业务。

### 10.4 穿透、击穿和雪崩的最小处理

- 穿透：只有一个固定全局 key，不接受用户任意 key，因此不存在无限不存在 ID 穿透；
- 击穿：TTL 过期瞬间可能有少量重复聚合查询，本项目接受，查询规模小且不为此引入分布式锁；
- 雪崩：只有一个 60 秒 key，不存在大量同刻过期 key；Redis 故障时直接降级 MySQL；
- 一致性：MySQL 真源 + 提交后失效 + 短 TTL，属于最终一致缓存，不宣称强一致。

## 11. Redis 固定窗口限流

### 11.1 原子算法

使用一段 Lua 脚本在 Redis 内原子完成：

```text
count = INCR key
if count == 1 then PEXPIRE key windowMillis end
ttl = PTTL key
return count, ttl
```

应用根据返回的 count 判断是否超过阈值；`Retry-After = ceil(ttl / 1000)`，至少为 1 秒。key 必须设置 TTL；若发现 `PTTL=-1`，脚本补设 TTL，避免永久封禁。

### 11.2 登录限流

| 项目 | 决策 |
|---|---|
| 路径 | `POST /api/auth/login` |
| key | `finguard:ratelimit:login:v1:{ipHash}:{usernameHash}` |
| 默认阈值 | 5 次 |
| 默认窗口 | 300 秒 |
| 计数时机 | DTO 校验和用户名规范化后、密码验证前 |
| 失败登录 | 计数 |
| 成功登录 | JWT 签发后删除当前 key |
| Redis 故障 | fail-open，继续现有登录 |

IP 使用应用实际看到的 remote address；只有配置可信代理后才能接受转发头。用户名和 IP 使用 UTF-8 规范值的 SHA-256 十六进制摘要，key 不含明文用户名、密码、JWT 或凭据状态。不存在用户和错误密码保持统一 `401 INVALID_CREDENTIALS`；限流不改变防枚举响应。

### 11.3 上传限流

| 项目 | 决策 |
|---|---|
| 路径 | `POST /api/import-jobs` |
| key | `finguard:ratelimit:upload:v1:user:{userId}` |
| 默认阈值 | 10 次 |
| 默认窗口 | 60 秒 |
| 计数时机 | ADMIN 认证/授权后、进入文件解析/哈希/数据库前 |
| 重复/无效文件 | 已消耗服务器入口资源，计数 |
| 匿名/REVIEWER | 在 Security 层拒绝，不消耗上传配额 |
| Redis 故障 | fail-open，继续现有上传 |

Servlet 在 Controller 前拒绝的超大 multipart 可能不会进入业务限流，这是已知限制；请求大小仍由现有 multipart 上限保护。

### 11.4 429 响应

沿用统一错误体并增加响应头：

```text
HTTP 429 Too Many Requests
Retry-After: 42
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

不返回当前计数、阈值、Redis key、用户名是否存在或内部异常。

## 12. 事务、一致性与幂等总表

| 场景 | 真源/保护 | 失败行为 |
|---|---|---|
| 文件重复上传 | `import_jobs.file_hash` 唯一 | 返回原任务，不重复审计 |
| 重复 MQ | 任务行锁、终态短路、现有唯一键 | ACK 已完成任务，无副作用 |
| 风险重复评估 | `uk_risk_hits_result_rule` | 同一结果/规则一条命中 |
| 审核任务重复生成 | 两个来源唯一索引 | 同一来源一条任务 |
| 并发审核 | status + version 条件更新 | 一人成功，其余 409 |
| 审计重复 | action + 目标唯一索引 | 每项业务动作一条审计 |
| 统计缓存陈旧 | 提交后失效 + 60s TTL | 最终回到 MySQL 真源 |
| Redis 故障 | fail-open + DB 业务约束 | 限流临时降级，业务仍正确 |

Redis 不参与风险、审核或审计的唯一性判定。即使清空 Redis，数据库约束仍必须保持全部业务不变量。

## 13. 自动化与真实验收矩阵

### 13.1 规则单元测试

- 每条规则的 disabled、命中、不命中和边界值；
- `BigDecimal` 精确比较，不发生静默舍入；
- Duplicate 的 5 分钟边界、排序方向、账户/方向/金额差异；
- Frequent 的第 4/5 笔、10 分钟边界、账户/方向隔离；
- 规则执行顺序稳定，一个交易可多规则命中；
- 配置非法时启动失败且不泄漏配置材料。

### 13.2 MySQL 与事务测试

- 干净 V1→V8/V9/V10、表数量、FK RESTRICT、CHECK、唯一键和查询索引；
- 规则命中批量写入及重复/并发评估幂等；
- 对账规则异常时 results/hits/tasks/completed 全部回滚；
- 三类对账异常和三类风险命中的任务生成矩阵；
- 500+ 交易批量处理无逐行 Mapper N+1；
- 审核条件更新两个并发线程只有一个成功；
- 审核状态与审计同提交/回滚；
- 五类审计动作唯一、actor/target 形状和敏感信息屏蔽。

### 13.3 Controller 与安全测试

- REVIEWER 可查询并决策，ADMIN 可查询但决策 403；
- 匿名 401、无已知角色 403、缺失任务 404；
- 参数 400、终态 409、过期 version 409；
- 统一错误体字段完整，拒绝请求无数据库副作用；
- audit GET 仅 ADMIN；statistics GET 允许 ADMIN/REVIEWER。

### 13.4 Redis 测试

- 首次统计 miss → DB → cache，第二次 hit 不查询 DB；
- 业务提交后 key 失效，事务回滚不失效；
- TTL 到期重新聚合，JSON 结构稳定；
- 并发固定窗口不超过阈值，`Retry-After` 合法，窗口后恢复；
- 登录成功清除当前 key，失败凭据继续计数；
- 匿名/REVIEWER 上传不消耗 ADMIN 上传配额；
- 停止 Redis 后统计走 DB、登录/上传按 fail-open 继续，MySQL 唯一性不受影响。

### 13.5 Day 7 真实验收

- MySQL、RabbitMQ、Redis 均 healthy，应用 `/actuator/health` 为 `UP`；
- ADMIN 上传并触发异步对账，轮询到风险命中和 PENDING 审核任务；
- REVIEWER 查询并确认/忽略，两个并发决策一个 200、一个 409；
- SQL 核对结果、风险、任务、版本、审核人和审计记录；
- 统计缓存命中/失效可观察，登录/上传真实返回 429 并在窗口后恢复；
- 停止 Redis 验证降级，恢复后不出现重复业务副作用；
- 完整 `mvn clean test` 记录实际数字，清理数据/key/消息/端口。

## 14. Day 2～Day 6 文件计划

### Day 2：风险持久层与规则骨架

新增/修改候选：

```text
docs/design/week5-day2-risk-persistence-and-rule-engine-design.md
src/main/resources/db/migration/V8__create_risk_hit_table.sql
src/main/java/com/finguard/core/risk/entity/RiskHit.java
src/main/java/com/finguard/core/risk/mapper/RiskHitMapper.java
src/main/java/com/finguard/core/risk/model/RiskRuleCode.java
src/main/java/com/finguard/core/risk/model/RiskReasonCode.java
src/main/java/com/finguard/core/risk/rule/RiskRule.java
src/main/java/com/finguard/core/risk/rule/RiskRuleResult.java
src/main/java/com/finguard/core/risk/rule/RiskEvaluationContext.java
src/test/java/com/finguard/core/risk/
src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java
```

回滚只删除 V8 之后的本日代码/测试（若迁移已应用则按项目数据库重建流程处理测试环境），不修改 V1～V7。

### Day 3：规则执行与任务生成

```text
docs/design/week5-day3-risk-rules-and-review-task-design.md
src/main/resources/db/migration/V9__create_review_task_table.sql
src/main/java/com/finguard/core/risk/config/RiskProperties.java
src/main/java/com/finguard/core/risk/rule/LargeAmountRule.java
src/main/java/com/finguard/core/risk/rule/DuplicateTransactionRule.java
src/main/java/com/finguard/core/risk/rule/FrequentTransactionRule.java
src/main/java/com/finguard/core/risk/service/RiskEvaluationService.java
src/main/java/com/finguard/core/review/entity/ReviewTask.java
src/main/java/com/finguard/core/review/mapper/ReviewTaskMapper.java
src/main/java/com/finguard/core/review/service/ReviewTaskGenerator.java
src/main/java/com/finguard/core/reconciliation/service/impl/ReconciliationJobTransactionService.java
src/test/java/com/finguard/core/risk/
src/test/java/com/finguard/core/review/
```

回滚恢复到“对账完成但不评估风险/不生成审核任务”，不删除既有对账结果或重放任务。

### Day 4：审核接口与乐观锁

```text
docs/design/week5-day4-review-workflow-and-optimistic-lock-design.md
src/main/java/com/finguard/core/review/controller/ReviewTaskController.java
src/main/java/com/finguard/core/review/dto/ReviewTaskQueryRequest.java
src/main/java/com/finguard/core/review/dto/ReviewDecisionRequest.java
src/main/java/com/finguard/core/review/service/ReviewTaskService.java
src/main/java/com/finguard/core/review/vo/ReviewTaskResponse.java
src/main/java/com/finguard/core/review/exception/
src/main/java/com/finguard/core/common/exception/ErrorCode.java
src/main/java/com/finguard/core/common/exception/GlobalExceptionHandler.java
src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java
src/test/java/com/finguard/core/review/
```

回滚只移除审核 HTTP/Service 决策能力；V9 表和已生成 PENDING 任务保留。

### Day 5：审计日志

```text
docs/design/week5-day5-audit-log-design.md
src/main/resources/db/migration/V10__create_audit_log_table.sql
src/main/java/com/finguard/core/audit/
src/main/java/com/finguard/core/importjob/service/impl/ImportJobTransactionService.java
src/main/java/com/finguard/core/reconciliation/service/impl/ReconciliationJobTransactionService.java
src/main/java/com/finguard/core/review/service/
src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java
src/test/java/com/finguard/core/audit/
```

回滚审计接入时不能回退已完成的风险和审核能力，也不能删除已有审计证据来掩盖问题。

### Day 6：Redis、统计与限流

```text
docs/design/week5-day6-redis-cache-and-rate-limit-design.md
pom.xml
docker-compose.yml
.env.example
src/main/resources/application.yml
src/main/java/com/finguard/core/statistics/
src/main/java/com/finguard/core/ratelimit/
src/main/java/com/finguard/core/auth/controller/AuthController.java
src/main/java/com/finguard/core/importjob/controller/ImportJobController.java
src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java
src/main/java/com/finguard/core/common/exception/ErrorCode.java
src/test/java/com/finguard/core/statistics/
src/test/java/com/finguard/core/ratelimit/
```

回滚 Redis 能力后核心 MySQL/RabbitMQ 业务必须仍可运行；不得回退 V8～V10 或风险/审核/审计数据。

## 15. Day 1 验收清单

- [x] 当前事实与 Week 5 计划已分开，未把风险、审核、审计或 Redis 描述为已实现。
- [x] 业务术语、模块依赖、正常链路和八类失败/降级流程已定义。
- [x] 三条规则的输入、默认阈值、窗口、原因码、边界、批量查询和幂等已锁定。
- [x] 对账异常/风险命中到审核任务的生成矩阵已锁定。
- [x] 审核状态、version 条件更新、接口、权限和 400/401/403/404/409 已定义。
- [x] V8～V10 三张表的字段、外键、唯一键、CHECK、索引和迁移纪律已定义。
- [x] 五类审计事件的 actor、initiatedBy、target、outcome、摘要、事务和幂等已定义。
- [x] 统计响应、Redis key/TTL/失效/降级和缓存风险最小处理已定义。
- [x] 登录/上传限流 key、阈值、窗口、原子脚本、429 和 fail-open 已定义。
- [x] 自动化/真实验收矩阵和 Day 2～Day 6 文件/回滚计划已形成。
- [x] Day 1 未新增 Java、SQL、依赖、配置或 Docker 服务。
- [x] 完整 `mvn clean test`、敏感信息检查、`git diff --check` 和提交前范围审计均已完成。

### 15.1 实际验收结果（2026-08-02）

- 完整 `mvn clean test`：287/287，0 failures、0 errors、0 skipped，`BUILD SUCCESS`；
- 基础设施：MySQL 8.4.10 与 RabbitMQ 4.3.4 均为 `healthy`；
- 范围：仅修改 `TASKS.md` 并新增本文，没有 Java、SQL、依赖、配置或 Docker 服务变更；
- 安全与质量：未发现密码、JWT、私钥或环境变量赋值，`git diff --check` 与提交前范围审计通过；
- Day 1 是设计里程碑，没有运行时行为变化，因此不伪造 HTTP/数据库功能验收；真实风险、审核、审计与 Redis 验收按 Day 2～Day 7 的实现边界执行。

## 16. 学习与面试重点

- 风险规则输出是“可解释候选”，不是概率模型或最终欺诈结论；
- 策略对象只计算，Service 编排查询、持久化和事务，职责不要混合；
- 乐观锁保护的是“基于旧版本提交”的并发覆盖，最终约束在 MySQL，不需要 Redis 锁；
- 审核状态和审计日志必须同事务，否则会出现有结论无证据或有证据无结论；
- 缓存旁路是“先看 Redis，miss 查 MySQL，再写 Redis”，但写路径必须在提交后失效；
- fail-open 让 Redis 故障不阻断核心业务，同时接受限流临时失效的安全代价；
- 数据正确性由数据库约束、事务、行锁、唯一键和 version 保证，Redis 只能增强性能和入口保护。

## 17. 回滚与提交

Day 1 只允许回退本文和 `TASKS.md` 对应状态/结论；不得修改或删除 V1～V7、Week 4 代码、MySQL/RabbitMQ 数据卷或现有测试。若完整回归产生 `target/`，它是已忽略的可再生输出，不进入提交。

建议提交：

```text
docs: design week 5 risk review redis and audit contract
```
