# Week 5 Day 2：风险命中持久层与规则契约设计

## 1. 状态与目标

- **状态**：已实现并完成真实验收。
- **当前基线**：Week 5 Day 1 已完成；Flyway 最新为 V7，存在 11 张业务表，完整回归基线为 287/287。
- **本日目标**：新增 V8 `risk_hits` 作为风险命中 MySQL 真源，并建立可供 Day 3 使用的规则输入、输出和批量持久化契约。
- **运行时影响**：Day 2 不接入现有对账事务，因此真实异步导入/对账行为保持不变。

## 2. 业务位置与数据流

Day 3 的目标调用链如下，Day 2 只实现其中加粗的契约和持久层：

```text
已持久化的 reconciliation_results + CSV transactions
  → 批量加载历史候选
  → RiskEvaluationContext
  → RiskRule.evaluate(context)
  → Optional<RiskRuleResult>
  → RiskHit
  → RiskHitMapper.insertBatch
  → RiskHitMapper.selectByReconciliationResultIds
  → Day 3 生成 review_tasks
```

无命中时返回 `Optional.empty()`，不得创建空 `risk_hits`。同一个对账结果可以命中多条不同规则，但同一规则只能留下一个风险命中。

## 3. 本日范围

包含：

- V8 `risk_hits`；
- `RiskRuleCode`、`RiskReasonCode`；
- `RiskHit`、`RiskHitMapper`；
- `RiskRule`、`RiskEvaluationContext`、`RiskRuleResult`；
- 规则契约单元测试、风险持久层集成测试、数据库基线验收。

不包含：

- 三条具体规则、类型安全配置、候选查询和 `RiskEvaluationService`；
- 修改 `ReconciliationJobTransactionService` 或 RabbitMQ 消费链；
- V9 `review_tasks`、审核 HTTP、乐观锁；
- V10 审计、Redis、统计、限流、监控或前端；
- 修改 V1～V7。

## 4. V8 数据模型

迁移名：`V8__create_risk_hit_table.sql`。

| 字段 | 类型 | 约束/用途 |
|---|---|---|
| `id` | BIGINT | 自增主键 |
| `reconciliation_result_id` | BIGINT | 非空，外键指向 `reconciliation_results.id`，删除 RESTRICT |
| `rule_code` | VARCHAR(32) | 三条固定规则之一 |
| `reason_code` | VARCHAR(64) | 必须与规则固定配对 |
| `observed_amount` | DECIMAL(19,2) | 仅大额规则使用 |
| `threshold_amount` | DECIMAL(19,2) | 仅大额规则使用，保存阈值快照 |
| `observed_count` | INT UNSIGNED | 重复/高频规则使用 |
| `threshold_count` | INT UNSIGNED | 重复/高频规则使用，保存阈值快照 |
| `window_seconds` | INT UNSIGNED | 重复/高频规则使用，保存窗口快照 |
| `reason_summary` | VARCHAR(255) | 服务端固定安全摘要 |
| `created_at` | DATETIME(3) | 数据库生成命中时间 |

### 4.1 关系与幂等

```text
reconciliation_results 1 ── * risk_hits
```

`reconciliation_result_id` 已能追溯 reconciliation job 和 CSV transaction，所以不重复保存 `reconciliation_job_id` 或 `transaction_id`。唯一键：

```text
uk_risk_hits_result_rule(reconciliation_result_id, rule_code)
```

该唯一键是风险重复评估的最终兜底。不得用 `INSERT IGNORE`，因为它会同时吞掉未知外键、非法 CHECK 等非预期错误。

### 4.2 合法记录形状

| ruleCode | reasonCode | 金额字段 | 次数字段 | window |
|---|---|---|---|---|
| `LARGE_AMOUNT` | `AMOUNT_AT_OR_ABOVE_THRESHOLD` | observed/threshold 非空且 observed >= threshold | 全空 | 空 |
| `POSSIBLE_DUPLICATE` | `SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME` | 全空 | observed/threshold 非空，threshold=1，observed >= threshold | 正数 |
| `FREQUENT_TRANSACTION` | `EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD` | 全空 | observed/threshold 非空，observed >= threshold | 正数 |

所有非空金额、次数、阈值和窗口均必须为正。摘要 trim 后必须非空，最大 255 字符。数据库使用规则枚举、原因枚举、正数、摘要和完整 shape CHECK 双重保护 Java 之外的写入。

### 4.3 索引

- 唯一键同时覆盖按 `reconciliation_result_id` 的外键/批量回查前缀；
- `idx_risk_hits_rule_created_id(rule_code, created_at DESC, id DESC)` 支持 Day 6 按规则统计和将来的稳定筛选；
- 没有实际查询依据时不增加 job、transaction 或单列 created_at 索引。

## 5. Java 契约

### 5.1 枚举

`RiskRuleCode` 固定执行顺序：

```text
LARGE_AMOUNT(10)
POSSIBLE_DUPLICATE(20)
FREQUENT_TRANSACTION(30)
```

`RiskReasonCode` 持有其所属 `RiskRuleCode`，从 Java 层拒绝错配。数据库 CHECK 再做最终兜底。

### 5.2 RiskEvaluationContext

使用不可变 record，包含：

- 正数 `reconciliationJobId`；
- 正数 `reconciliationResultId`；
- 当前不可变 `ReconciliationTransaction`；
- `ReconciliationResultType` 和 `ReconciliationReasonCode`；
- 同账户历史 `ReconciliationTransaction` 候选的防御性副本；
- 固定业务 `ZoneId`。

Context 只保存事实，不访问 Mapper，也不改变交易。Day 3 的类型安全配置由具体规则构造器持有，避免把三种互不相同的配置塞进通用 Map。

### 5.3 RiskRuleResult

使用不可变 record 保存 rule/reason、观测值、阈值快照、窗口和安全摘要。构造时按与 V8 相同的 shape 规则快速失败：

- 金额用 `BigDecimal`，scale 不超过 2，不使用 `double`；
- 次数和窗口用正整数；
- 观测值必须达到阈值；
- rule/reason 必须配对；
- 摘要 trim 后非空且不超过 255 字符。

提供三个命名工厂，减少调用方手写空字段组合：`largeAmount`、`possibleDuplicate`、`frequentTransaction`。

### 5.4 RiskRule

```text
ruleCode()
enabled()
evaluate(RiskEvaluationContext) -> Optional<RiskRuleResult>
getOrder() -> ruleCode.executionOrder
```

接口继承 Spring `Ordered`，使 Spring 注入的规则列表顺序稳定。规则必须是纯计算对象：不得查库、写库、开启事务、创建审核任务或修改输入。

### 5.5 RiskHitMapper

本日只提供两个业务查询路径：

1. `insertBatch(nonEmptyHits)`：一次多值 INSERT；
2. `selectByReconciliationResultIds(nonEmptyIds)`：一次 IN 查询，按 `reconciliation_result_id ASC, rule_code ASC, id ASC` 稳定返回。

Day 3 负责分批调用并处理空列表；Day 2 不创建只转发 Mapper 的空 Service。

## 6. 失败与事务语义

| 场景 | 预期 |
|---|---|
| 无风险命中 | 不调用批量 INSERT，不创建空记录 |
| 同结果、不同规则 | 允许分别保存 |
| 同结果、同规则 | 唯一键拒绝第二条 |
| 未知结果 ID | 外键拒绝 |
| rule/reason/shape 错配 | Java 或 CHECK 拒绝 |
| 删除已有命中的结果 | RESTRICT 拒绝 |
| Mapper/数据库异常 | 抛出，不吞掉；Day 3 事务整体回滚 |

Day 2 的持久层测试可以独立提交。Day 3 接入后，results、hits、review tasks 和 job COMPLETED 必须属于同一数据库事务。

## 7. 测试矩阵

### 7.1 规则契约单测

- Context 必填项、正数 ID、同账户候选和防御性复制；
- 三种合法 Result shape；
- rule/reason 错配、非正数、金额 scale、observed 小于 threshold、空/超长摘要；
- test-double 规则的 enabled、命中、无命中和 Spring Ordered 顺序。

具体规则的 10000 元、5 分钟、10 分钟、第 4/5 笔等业务边界属于 Day 3，不在本日伪造。

### 7.2 风险持久层集成测试

- 三种合法 shape 批量写入、枚举/金额/时间往返；
- 一批结果 ID 回查和稳定排序；
- 同结果不同规则共存；
- 同结果同规则唯一冲突；
- 未知外键、非法 rule/reason/shape/数值/摘要；
- `reconciliation_results` 删除 RESTRICT；
- 清理顺序为 risk hits → reconciliation results → jobs → transactions → import jobs → accounts/users。

### 7.3 数据库与真实验收

- 干净项目数据库从 V1→V8，V1～V7 checksum 不变；
- 最新版本为 8，业务表数为 12；
- `information_schema` 验证列、1 个 RESTRICT 外键、唯一键、2 个索引和 5 个命名 CHECK；
- `EXPLAIN` 证明按 result ID 回查使用唯一键前缀，按 rule code 查询使用规则索引；
- 聚焦测试、完整 `mvn clean test`、真实应用健康检查、数据/端口清理和 `git diff --check`。

## 8. 文件边界

新增：

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
src/test/java/com/finguard/core/risk/RiskHitPersistenceIntegrationTest.java
src/test/java/com/finguard/core/risk/rule/RiskRuleContractTest.java
```

修改：

```text
TASKS.md
src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java
src/test/java/com/finguard/core/reconciliation/support/ReconciliationTestFixture.java
```

共享 reconciliation 测试夹具只增加 V8 外键要求的 risk-first 清理，不改变生产行为。

## 9. 回滚

Java、测试和设计文档可按文件范围回退。V8 若仅应用于明确可重建的项目测试库，可随测试环境重建回到 V7；若已进入共享或需保留数据的数据库，则不得修改或删除已应用的 V8，只能新增补偿迁移。不得删除已有对账数据、Week 4 代码或 MySQL/RabbitMQ 数据卷来掩盖失败。

## 10. 实际验收结果（2026-08-02）

- 风险规则契约 + 风险持久层 + 数据库基线聚焦测试：18/18；
- 加入认证、导入历史最新版本断言后的扩展聚焦测试：30/30；
- 完整 `mvn clean test`：297/297，0 failures、0 errors、0 skipped，`BUILD SUCCESS`；
- 独立临时数据库从空库执行 V1→V8，最新版本 8，共 12 张业务表，V1～V7 的 7 个 checksum 与当前项目库一致；
- `information_schema` 验证 1 个 RESTRICT 外键、二列唯一键、三列规则索引、5 个 CHECK 和 `DECIMAL(19,2)`；
- 按 result ID 批量回查使用 `uk_risk_hits_result_rule`，按 rule code 统计使用 `idx_risk_hits_rule_created_id`；
- 真实应用使用进程内临时 256-bit JWT 密钥启动于 18080，`GET /actuator/health` 返回 `HTTP 200 {"status":"UP"}`；
- `risk_hits`、风险测试用户/账户、临时验收数据库、RabbitMQ ready/unacked 消息均为 0，18080 已释放；
- 未新增具体规则、风险评估 Service、对账事务接入、审核、审计、Redis、Controller 或 HTTP 路由。
