# Week 5 Day 3：风险规则与审核任务生成设计

## 1. 状态与目标

- **状态**：已实现并完成真实验收。
- **进入基线**：Week 5 Day 2 提交 `0cbb71a`，Flyway V8，12 张业务表，`risk_hits` 与规则输入/输出契约已存在，完整回归 297/297。
- **本日目标**：在对账业务事务内完成风险评估、风险命中持久化和 `PENDING` 审核任务生成，保证可解释、无 N+1、重复消息无副作用且失败整体回滚。

## 2. 范围

### 2.1 包含

- 类型安全 `RiskProperties` 和启动校验；
- `LargeAmountRule`、`DuplicateTransactionRule`、`FrequentTransactionRule`；
- 历史 `CSV_IMPORT` 交易的批量预加载和内存窗口计算；
- `RiskEvaluationService`、V9 `review_tasks`、持久层与 `ReviewTaskGenerator`；
- 对账结果、风险命中、审核任务和 job 终态的同事务提交；
- 规则、数据库、编排、幂等、回滚与真实异步闭环验收。

### 2.2 不包含

- 审核分页/详情/decision HTTP API、RBAC 变更、乐观锁和审核错误码；
- Redis、限流、审计日志、新 RabbitMQ 事件或 Outbox 类型；
- Drools、规则 DSL、动态配置平台、复杂评分模型或通用工作流。

## 3. 数据与事务流

```text
processPending(jobId) 锁定 reconciliation_jobs
  → 加载本次 CSV 交易和 MANUAL 对账候选
  → matcher 计算 decisions
  → 批量 INSERT reconciliation_results
  → 按 jobId 回查结果及数据库 ID
  → 按 accountIds + 总时间边界批量查历史 CSV 候选
  → 按结果执行已启用规则
  → 批量 INSERT risk_hits，按 resultIds 回查 hit IDs
  → 为对账异常和每条 hit 批量 INSERT review_tasks
  → 回查来源并核对数量
  → 更新 job COMPLETED 与四类计数
  → COMMIT
  → consumer ACK
```

上述步骤全部参与 `ReconciliationJobTransactionService.processPending` 已有事务。任一规则、Mapper、计数校验或 job 更新失败都抛出，不允许保留部分 results/hits/tasks。终态重投在行锁后返回 `ALREADY_COMPLETED`，不重跑规则。

## 4. 规则配置

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
      window: 10m
      threshold-count: 5
```

校验规则：

- 金额阈值必须大于 0，scale 不超过 2；
- 时间窗口必须大于 0、是整秒且不超过 `Integer.MAX_VALUE` 秒；
- 高频阈值必须大于 0；
- 配置只在启动时绑定，不从 Redis/数据库动态刷新；
- 命中保存当次阈值和窗口快照，后续改配置不改写历史证据。

## 5. 三条规则

### 5.1 LARGE_AMOUNT

- 对本次每条未删除 CSV 交易执行，不区分收入/支出；
- `amount.compareTo(threshold) >= 0` 命中；
- 默认阈值 `10000.00`；
- 摘要是服务端固定文本，不包含描述、原始 CSV 或配置 JSON。

### 5.2 POSSIBLE_DUPLICATE

- 默认窗口 `[currentTime - 5m, currentTime]`，含边界；
- 候选必须同账户、同方向、精确同金额、不同 ID；
- 候选 `(transaction_time, id)` 必须小于当前交易，只让后发交易命中；
- 命中时 `threshold_count=1`，`observed_count` 保存更早同额候选数。

### 5.3 FREQUENT_TRANSACTION

- 只评估 `EXPENSE`；
- 默认窗口 `[currentTime - 10m, currentTime]`，含两端；
- 候选只统计同账户、`EXPENSE`、排序不晚于当前 `(transaction_time, id)` 的 CSV 交易；
- 历史候选可跨 import job；默认第 5 笔及之后命中。

## 6. 候选查询与复杂度

`RiskEvaluationService` 先取本次交易的去重 account IDs、最早和最晚交易时间。查询边界为：

```text
from = min(current.transaction_time) - max(enabled duplicate/frequent window)
to   = max(current.transaction_time)
```

account IDs 每 500 个一批查询 `source='CSV_IMPORT' AND deleted=0`，按 `(account_id, transaction_time, id)` 排序。回到 Java 后按 account 分组，规则在组内做时间和业务字段过滤。禁止按交易或规则逐条查库。

现有 `idx_transactions_account_time(account_id, transaction_time)` 是候选索引；是否增加 source/deleted 只能根据真实 `EXPLAIN` 和当前数据分布决定，Day 3 不预先猜测建索引。

## 7. V9 review_tasks

| 字段 | 形状 |
|---|---|
| `id` | BIGINT PK AUTO_INCREMENT |
| `source_type` | `RECONCILIATION_EXCEPTION/RISK_HIT` |
| `reconciliation_result_id` | 可空，FK RESTRICT |
| `risk_hit_id` | 可空，FK RESTRICT |
| `status` | `PENDING/CONFIRMED/IGNORED` |
| `version` | PENDING=0，终态=1 |
| `reviewed_by` | 可空，FK users RESTRICT |
| `reviewed_at` | 可空 |
| `decision_note` | 可空，最多 255 字符 |
| `created_at/updated_at` | DATETIME(3) |

关键约束：

- `chk_review_tasks_source`：来源枚举与两个来源外键严格二选一；
- `chk_review_tasks_state`：PENDING 必须 version=0 且审核字段全空，终态必须 version=1 且 reviewer/time 非空；
- 两个来源唯一键保证同一来源只生成一件任务；
- 三个外键都使用 RESTRICT，不让删除证据/审核人时级联删除审核记录；
- 索引 `(status, created_at DESC, id DESC)` 与 `(source_type, status, created_at DESC, id DESC)` 为 Day 4 稳定分页准备。

## 8. 任务生成矩阵

| 输入 | 输出 |
|---|---|
| MATCHED 且无 hit | 无任务 |
| UNMATCHED | 1 个 RECONCILIATION_EXCEPTION |
| DUPLICATE | 1 个 RECONCILIATION_EXCEPTION |
| SUSPICIOUS | 1 个 RECONCILIATION_EXCEPTION |
| 每条 risk_hit | 1 个 RISK_HIT |

同一对账结果可同时有一个异常任务与多个风险任务，因为它们表达不同事实。生成器只创建 `PENDING(version=0)`，不提供决策方法。

## 9. 幂等与失败语义

| 场景 | 结果 |
|---|---|
| 终态 MQ 重投/ACK 丢失 | job 行锁后短路，不新增任何记录 |
| 并发消费 | job 行锁串行，一个 PROCESSED、一个 ALREADY_COMPLETED |
| 同 result/rule 重复 | `uk_risk_hits_result_rule` 最终拒绝 |
| 同审核来源重复 | V9 来源唯一键最终拒绝 |
| 规则或 Mapper 失败 | 整个事务回滚，job 不是 COMPLETED |
| 重试可恢复故障 | 现有 5s/30s 链路重试 |
| 重试耗尽/业务失败 | 保留现有 FAILED/DLQ 语义 |

不使用 `INSERT IGNORE`；不将未分类唯一键冲突当作成功；不用 Redis 保证最终幂等。

## 10. 测试与验收

### 10.1 规则单测

- 9999.99/10000.00/10000.01；
- 5 分钟边界与 5 分 1 毫秒；
- 同时刻 ID 先后，不同账户/方向/金额；
- 10 分钟边界、第 4/5 笔、收入不计数；
- 规则关闭、配置非法快速失败。

### 10.2 MySQL 与事务

- 干净库 V1→V9，13 表，V1～V8 checksum 不变；
- V9 合法/非法来源与状态形状、未知外键、RESTRICT、唯一键和索引；
- 无风险、单规则、多规则、三类异常、跨导入历史候选；
- 重复/并发 MQ、ACK 丢失红投、规则/风险/审核写入失败整体回滚。

### 10.3 真实闭环

- 真实 MySQL/RabbitMQ 健康；
- ADMIN JWT 上传 CSV，等待异步导入终态；
- 通过已有 HTTP 接口触发对账，等待异步对账终态；
- SQL 核对 results/hits/tasks 数量、形状和 job COMPLETED；
- 完整 `mvn clean test`、健康检查、数据/消息/端口清理、敏感信息检查、范围审计和 `git diff --check`。

## 11. 实际验收结果（2026-08-02）

- 规则/配置/契约 13/13，V9/数据库基线 13/13，风险事务/并发/回滚 10/10，扩大聚焦回归 67/67，全部 0 failures、0 errors、0 skipped；
- 完整 `mvn clean test` 314/314，0 failures、0 errors、0 skipped，`BUILD SUCCESS`；
- 独立空库从 V1→V9，13 张业务表，V1～V8 checksum 全部与项目库一致；
- V9 已验证 3 个 RESTRICT 外键、4 个 CHECK、2 个唯一键和 2 个分页索引；
- 4,000 条合成交易的真实 `EXPLAIN` 估算命中 15.89% 并选择全表扫描。当前小规模/低选择性样本不能证明新索引有净收益，本日不添加猜测型索引，留待 Week 6 用更真实数据规模压测；
- 真实应用健康为 `UP`，JWT/HTTP 异步导入 `SUCCESS|5|5`，对账 `COMPLETED|5|0|5|0|0`；SQL 得到 5 results、10 hits（大额 5/疑似重复 4/高频 1）和 15 个 `PENDING(version=0)` tasks（异常 5/风险 10）；
- 规则异常、risk insert 失败和 review insert 失败均证明 results/hits/tasks/job 终态整体回滚并可重试恢复；并发 MQ 与 ACK 丢失红投无重复副作用；
- 验收数据、临时数据库、脚本、凭据和端口已清理；8 个 RabbitMQ 业务队列 ready/unacked 全为 0。

## 12. Day 4 交接边界

Day 3 留下已受数据库约束保护的 `PENDING(version=0)` 任务和持久化查询基础。Day 4 再实现 HTTP 分页/详情、ADMIN/REVIEWER 查询、REVIEWER 决策、条件更新乐观锁和 404/409 错误分类。

## 13. 回滚与提交

Java、测试、配置和文档可按 Day 3 文件范围回退。V9 在可重建测试库中可通过重建环境回到 V8；如已用于共享/保留数据的库，不得修改或删除 V9，只能追加补偿迁移。

提交建议：

```text
feat: add risk evaluation and review task generation
```
