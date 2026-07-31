# Week 3 综合验收与复盘

## 1. 验收范围

本周交付了 CSV 导入与同步自动对账闭环：文件请求检查、原始字节 SHA-256、严格 UTF-8/BOM 与 RFC 4180 解析、六字段业务校验、批量账户解析、重复判断、同步任务状态流转、500 行批量写入、失败恢复、自动对账、结果分页和 ADMIN/REVIEWER 权限控制。

本日只做综合验收、复盘和清理，没有新增 RabbitMQ、Redis、风险规则、人工审核、审计日志或前端能力。

## 2. 按 Day 复核

| Day | 实际交付 | 复核结论 |
|---|---|---|
| Day 1 | CSV 文件、字段、幂等、失败和状态契约 | 文件级错误与行级错误边界清晰；`PENDING -> PROCESSING -> SUCCESS/PARTIAL_SUCCESS/FAILED` 可指导实现 |
| Day 2 | `import_jobs`、`import_row_errors`、V4 持久层 | 文件哈希唯一、错误码/统计约束、外键和稳定分页已落地 |
| Day 3 | 文件元数据、SHA-256、严格解析 | 哈希基于原始字节；UTF-8/BOM、换行、表头、引号和逻辑记录号有测试覆盖 |
| Day 4 | 行规范化、账户解析、重复判断 | 文件内重复、数据库重复与交易业务唯一键职责分离 |
| Day 5 | 同步 CSV 上传与入库 | 成功/部分成功、幂等、批量写入、唯一键竞态和失败恢复已覆盖 |
| Day 6 | 同步自动对账 | 强流水号优先、金额/方向/时间窗口、一对一分配、四类结果、分页和失败恢复已覆盖 |

## 3. 测试证据

- 聚焦验收：34/34 通过，包含数据库基线、CSV 导入、导入恢复、自动对账和 JWT/RBAC 集成。
- 完整回归：`mvn clean test`，230/230 通过，0 failures、0 errors、0 skipped。
- 失败恢复：`SyncImportRecoveryIntegrationTest` 和 `ReconciliationJobIntegrationTest` 注入持久化异常，确认交易/结果回滚且任务进入 `FAILED`。
- 并发幂等：对账集成测试覆盖串行与并发重复触发，数据库唯一键作为最终兜底。

## 4. 真实 MySQL、JWT 与 HTTP 证据

在重建的项目专属 MySQL 数据卷上，应用从空库成功执行 V1→V6；启动日志显示 Flyway 已应用 6 个迁移，应用健康检查返回 `{"status":"UP"}`。

真实 ADMIN/REVIEWER JWT + HTTP 验收结果：

| 场景 | 结果 |
|---|---|
| ADMIN 上传两行 CSV | `201`，任务 `SUCCESS`，`totalRows=2`、`successRows=2` |
| ADMIN 重复上传相同原始字节 | `200`，`duplicateFile=true`，复用任务 ID |
| REVIEWER 查询任务与错误分页 | `200`，任务为 `SUCCESS`，错误总数为 0 |
| ADMIN 触发对账 | `201`，任务 `COMPLETED`，2 条结果中 1 条 `MATCHED`、1 条 `UNMATCHED` |
| ADMIN 重复触发对账 | `200`，`duplicateRequest=true`，复用任务 ID |
| REVIEWER 查询匹配结果 | `200`，筛选结果总数为 1，类型为 `MATCHED` |
| REVIEWER 上传/触发 | `403` |
| REVIEWER 查询不存在导入任务 | `404` |

SQL 核对得到：`flyway=6`、验收时 `reconciliation_jobs` 统计为 `COMPLETED|2|1|1|0|0`，结果行数为 2，和任务统计守恒。

## 5. 清理与范围审计

- 已停止 Spring Boot 应用，8080 端口已释放。
- 已按外键依赖顺序清理验收用户、账户、交易、导入任务、对账任务和结果；清理后相关计数全部为 0。
- 已确认 Week 4–6 的 RabbitMQ、Redis、风险、审核、审计和监控能力没有进入本日改动。
- 已执行 `git diff --check`。

## 6. Week 4 边界

Week 4 从当前同步流程演进到 RabbitMQ 异步化，重点是 Publisher Confirm、手动 ACK、消费幂等、重试和死信队列。同步版本的状态机、失败恢复和幂等约束作为异步化的基线，不在本日提前实现。
