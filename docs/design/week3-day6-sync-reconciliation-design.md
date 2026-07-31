# Week 3 Day 6：同步版自动对账设计

## 1. 目标与边界

Day 6 以一个已经完成的 CSV 导入任务为执行边界，把该任务成功写入的
`CSV_IMPORT` 交易与系统中的 `MANUAL` 交易进行可解释的一对一匹配：

```text
import job
  → reconciliation job
  → load CSV transactions and MANUAL candidates in batches
  → deterministic two-pass matching
  → MATCHED / UNMATCHED / DUPLICATE / SUSPICIOUS
  → persist results and counters atomically
```

本日只实现同步触发和查询，不在上传事务内自动调用。RabbitMQ、重试、
死信、风险规则、人工审核、乐观锁、审计和 Redis 均不进入 Day 6。
对账只新增结果数据，不更新、软删除或覆盖原始交易。

## 2. HTTP 契约与权限

```http
POST /api/reconciliation-jobs
Content-Type: application/json

{"importJobId": 123}
```

- 首次创建并同步执行后返回 `201 Created`。
- `Location` 为 `/api/reconciliation-jobs/{id}`。
- 相同导入任务重复或并发触发返回原任务 `200 OK`，
  `duplicateRequest=true`，不重复执行。
- 只有 `ADMIN` 可以触发。

```http
GET /api/reconciliation-jobs/{id}
GET /api/reconciliation-jobs/{id}/results?page=1&size=20&resultType=MATCHED
```

- 查询允许 `ADMIN` 和 `REVIEWER`。
- 结果默认 `page=1,size=20`，最大 100。
- `resultType` 可空；存在时进行精确枚举筛选。
- 结果固定按 `csv_transaction_id ASC, id ASC`。

输入导入任务必须：

- 存在；
- 状态为 `SUCCESS` 或 `PARTIAL_SUCCESS`；
- `success_rows > 0`；
- 实际存在至少一条未删除且属于该任务的 `CSV_IMPORT` 交易。

不存在返回 `404 IMPORT_JOB_NOT_FOUND`。状态或数据前置条件不满足返回
`409 INVALID_RECONCILIATION_OPERATION`。对账任务不存在返回
`404 RECONCILIATION_JOB_NOT_FOUND`。

## 3. V6 数据模型

新增且只新增：

```text
V6__create_reconciliation_tables.sql
```

不得修改 V1～V5。

### 3.1 reconciliation_jobs

| 字段 | 规则 |
|---|---|
| `id` | 主键 |
| `import_job_id` | 导入任务外键，唯一，保证触发幂等 |
| `status` | `PENDING/PROCESSING/COMPLETED/FAILED` |
| `total_count` | 参与对账的外部交易数 |
| `matched_count` | `MATCHED` 数 |
| `unmatched_count` | `UNMATCHED` 数 |
| `duplicate_count` | `DUPLICATE` 数 |
| `suspicious_count` | `SUSPICIOUS` 数 |
| `error_summary` | 可空安全失败摘要 |
| `created_by` | 触发用户外键 |
| `started_at/finished_at` | 状态时间 |
| `created_at/updated_at` | 记录时间 |

数据库约束：

```text
所有计数 >= 0
分类计数之和 <= total_count
COMPLETED 时分类计数之和 = total_count
FAILED 时 error_summary 非空
非 FAILED 时 error_summary 为空
```

### 3.2 reconciliation_results

| 字段 | 规则 |
|---|---|
| `id` | 主键 |
| `reconciliation_job_id` | 对账任务外键 |
| `csv_transaction_id` | 当前导入任务的外部交易 |
| `manual_transaction_id` | 可空候选内部交易 |
| `result_type` | 四类结果 |
| `match_method` | `EXACT/TOLERANCE/NONE` |
| `reason_code` | 稳定、可测试的主原因 |
| `created_at` | 创建时间 |

唯一键 `(reconciliation_job_id, csv_transaction_id)` 保证同一外部交易在
同一任务内只有一个结果。

原因码：

```text
EXACT_MATCH
TOLERANCE_MATCH
NO_CANDIDATE
MULTIPLE_CANDIDATES
MANUAL_ALREADY_MATCHED
DIRECTION_MISMATCH
AMOUNT_MISMATCH
TIME_OUT_OF_RANGE
```

结果形状约束：

- `MATCHED` 必须有内部交易，方法只能是 `EXACT/TOLERANCE`，
  原因分别为 `EXACT_MATCH/TOLERANCE_MATCH`。
- `UNMATCHED` 必须没有内部交易，方法为 `NONE`，原因为
  `NO_CANDIDATE`。
- `DUPLICATE` 方法为 `NONE`，原因为
  `MULTIPLE_CANDIDATES/MANUAL_ALREADY_MATCHED`。
- `SUSPICIOUS` 必须有同流水号内部候选，方法为 `NONE`，原因为三个
  字段冲突原因之一。

所有外键使用 `ON DELETE RESTRICT`，保留追踪证据。

## 4. 第一版规则

共同前提：

- 只比较同一账户；
- 外部侧只读取当前导入任务、`source=CSV_IMPORT`、`deleted=0`；
- 内部侧只读取 `source=MANUAL`、`deleted=0`；
- 外部流水号使用数据库现有大小写敏感语义；
- 方向必须相同；
- 金额使用 `BigDecimal.compareTo`，禁止 `double`；
- 时间容差为 `[csvTime-3 days, csvTime+3 days]`，包含边界；
- 描述不参与；
- 不允许金额容差、汇率或文本模糊匹配。

### 4.1 第一阶段：强标识候选

按 `(accountId, externalTransactionNo)` 查找内部候选。现有
`UNIQUE(account_id, source, external_transaction_no)` 保证同来源最多
一条。

按以下优先级只输出一个原因：

| 条件 | 结果 | 方法/原因 |
|---|---|---|
| 方向、金额、时间完全一致 | MATCHED | EXACT / EXACT_MATCH |
| 方向、金额一致，时间在 3 天内 | MATCHED | TOLERANCE / TOLERANCE_MATCH |
| 方向不同 | SUSPICIOUS | NONE / DIRECTION_MISMATCH |
| 金额不同 | SUSPICIOUS | NONE / AMOUNT_MISMATCH |
| 时间超窗 | SUSPICIOUS | NONE / TIME_OUT_OF_RANGE |

所有强标识候选都先保留其内部交易 ID。即使结果是 `SUSPICIOUS`，弱
匹配也不能把该内部交易分配给其他外部交易。

### 4.2 第二阶段：弱候选

仅处理没有强标识候选的外部交易。候选必须同时满足：

```text
same account
same direction
same amount
manual time within csv time ±3 days
```

| 候选情况 | 结果 |
|---|---|
| 0 | UNMATCHED / NO_CANDIDATE |
| 1 且未保留/未使用 | MATCHED / TOLERANCE_MATCH |
| 1 但已保留/已使用 | DUPLICATE / MANUAL_ALREADY_MATCHED |
| 大于 1 | DUPLICATE / MULTIPLE_CANDIDATES |

外部交易按 ID 升序，候选按 ID 升序。这样同一输入在不同 JVM 遍历顺序
下仍产生相同结果。

## 5. 批量查询

禁止每条外部交易执行一次 Mapper 查询。

查询分为：

1. 按 `import_job_id` 一次读取当前外部交易，按 ID 升序；
2. 将 `(accountId, externalTransactionNo)` 去重后每 500 个键查询强候选；
3. 将账户 ID 去重后每 500 个账户查询时间区间候选。区间为当前批次
   外部交易最小时间减 3 天到最大时间加 3 天；
4. Java 内存中按账户建立候选桶并执行逐条时间过滤。

强标识查询使用现有唯一键
`uk_transactions_account_source_external_no`。时间查询先验证现有
`idx_transactions_account_time`；只有真实 `EXPLAIN` 证明需要时才新增
索引。本日 V6 默认不修改 `transactions` 索引。

## 6. 状态机、事务与失败恢复

```text
PENDING → PROCESSING → COMPLETED
                     ↘ FAILED
```

事务边界：

```text
TX-1 REQUIRES_NEW read-only: 按 importJobId 查已有任务
TX-2 REQUIRES_NEW: 创建 PENDING
TX-3 REQUIRES_NEW: PENDING → PROCESSING
TX-4 REQUIRED: 读取、判定、批量写结果、汇总、COMPLETED
TX-5 REQUIRES_NEW: 未预期异常后 PROCESSING → FAILED
```

TX-4 内任意运行时异常都使结果和完成统计整体回滚。编排层随后调用
TX-5，保存固定摘要 `Reconciliation processing failed`。数据库异常、
SQL、文件路径、堆栈和第三方内部消息不能进入 API 或任务摘要。

状态更新 SQL 必须带原状态：

```sql
... WHERE id = ? AND status = 'PENDING'
... WHERE id = ? AND status = 'PROCESSING'
```

受影响行数不是 1 立即失败，禁止覆盖并发状态。

## 7. Java 组件

```text
controller
  ReconciliationJobController
service
  ReconciliationJobService
  ReconciliationJobServiceImpl          编排、响应映射
  ReconciliationJobTransactionService   事务边界、持久化
  ReconciliationMatcher                 纯规则
model
  ReconciliationJobStatus
  ReconciliationResultType
  ReconciliationMatchMethod
  ReconciliationReasonCode
  ReconciliationDecision
entity
  ReconciliationJob
  ReconciliationResult
mapper
  ReconciliationJobMapper
  ReconciliationResultMapper
dto / vo
  CreateReconciliationJobRequest
  ReconciliationResultQueryRequest
  ReconciliationJobResponse
  ReconciliationResultResponse
```

匹配器不依赖 Spring、HTTP 或数据库，以不可变输入输出承载纯业务
规则；事务 Service 负责候选加载、实体映射、批量写入和状态推进。

## 8. 测试矩阵

### 8.1 纯规则

- 精确匹配；
- 时间容差内及正负 3 天边界；
- 大小写不同的流水号不属于强候选；
- 方向、金额、时间冲突的原因优先级；
- 无候选；
- 单个弱候选；
- 多个弱候选；
- 已被强候选保留或已被前序弱匹配使用；
- 软删除候选不会进入输入；
- 输入顺序变化后按稳定 ID 仍得到相同结果；
- 四类计数守恒。

### 8.2 MySQL

- V1→V6、表/约束/外键/唯一键；
- 真实 `EXPLAIN`；
- 四类结果在一个任务内共存；
- 500 条以上输入分批且无 N+1；
- 串行、并发重复触发；
- 结果分页与类型筛选；
- 故障注入使 TX-4 回滚、TX-5 标记失败；
- 拒绝非法导入状态且无副作用。

### 8.3 HTTP

- ADMIN 首次触发 `201 + Location`；
- ADMIN 重复触发 `200 + duplicateRequest=true`；
- ADMIN/REVIEWER 查询详情和结果；
- REVIEWER 触发 `403`；
- 匿名请求 `401`；
- 不存在 `404`、非法状态 `409`、非法参数 `400`；
- SQL 校验任务计数和逐笔关联；
- 清理验收用户、任务、结果、账户和交易。

## 9. 完成条件

只有以下条件全部满足才关闭 Day 6：

1. 设计、V6、代码与测试范围一致；
2. 聚焦测试和 `mvn clean test` 全绿且无跳过；
3. MySQL `healthy`，真实 JWT/HTTP/SQL 验收通过；
4. 四类结果、幂等、回滚和权限均有证据；
5. 测试数据与 8080 端口已清理；
6. `git diff --check` 和范围审计通过；
7. `TASKS.md`、`README.md` 和 Git 索引更新；
8. 提交 `feat: implement synchronous reconciliation`。
