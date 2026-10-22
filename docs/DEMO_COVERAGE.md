# 完整控制台覆盖清单

入口：同源 `/demo/index.html`。下面区分业务 API、场景和工程证据；当次运行结果见 [验收报告](review/2026-10-09-complete-demo-console-acceptance.md)。页面入口与运行证据分别核对。

## 27 个业务操作 / 20 个路径对象

| 操作 | 页面入口 | 验证 |
| --- | --- | --- |
| POST /api/auth/login | ADMIN / REVIEWER 会话栏 | business / helpers 双角色登录 |
| GET /api/auth/me | 会话身份 | helpers 服务端身份、session Node 测试 |
| POST /api/accounts | 账户 / 创建 | business 六操作 |
| GET /api/accounts | 账户 / 筛选分页 | business、reliability 101 条 |
| GET /api/accounts/{id} | 账户 / 详情、ID | business、安全文本 |
| PATCH /api/accounts/{id}/name | 账户 / 保存名称 | business |
| PATCH /api/accounts/{id}/status | 账户 / 启用、停用 | business、确认取消 Node 测试 |
| DELETE /api/accounts/{id} | 账户 / 删除 | business、404、真实约束 |
| POST /api/transactions | 交易 / 创建人工交易 | business、十进制金额 |
| GET /api/transactions | 交易 / 全部筛选分页 | business、transactions Node 测试 |
| GET /api/transactions/{id} | 交易 / 详情、ID | business、CSV_IMPORT 只读 |
| PUT /api/transactions/{id} | 交易 / 保存交易 | business、完整替换体 |
| DELETE /api/transactions/{id} | 交易 / 删除 | business、404 |
| POST /api/import-jobs | 导入 / 任意文件、主动重复 | business、双击、原始字节 |
| GET /api/import-jobs | 导入 / 历史筛选分页 | business、Java 101 条稳定分页 |
| GET /api/import-jobs/{id} | 导入 / ID、终态轮询、继续 GET | business、刷新重新登录 |
| GET /api/import-jobs/{id}/errors | 导入 / 行错误分页 | business、仅 page/size |
| POST /api/reconciliation-jobs | 对账 / 创建、主动重复 | business、导入状态验证 |
| GET /api/reconciliation-jobs | 对账 / 历史筛选分页 | business、Java 101 条稳定分页 |
| GET /api/reconciliation-jobs/{id} | 对账 / ID、终态轮询 | business、GET 继续 |
| GET /api/reconciliation-jobs/{id}/results | 对账 / 逐笔结果分页 | business、resultType 服务端筛选 |
| GET /api/review-tasks | 审核 / 状态、来源、结果、规则 | business、互斥筛选 Node 测试 |
| GET /api/review-tasks/{id} | 审核 / ID、详情、刷新版本 | business、备注保留 |
| GET /api/review-tasks/{id}/context | REVIEWER / 上下文 | business、Java RBAC |
| PATCH /api/review-tasks/{id}/decision | REVIEWER / 确认、忽略 | business、两终态、409 |
| GET /api/audit-logs | ADMIN / 审计、完整批次 | reliability 第二页批次记录 |
| GET /api/statistics/overview | 审计统计 / 全数据库 | business、采集时间、缓存范围 |

## 服务端分支和场景

| 事实 | 配方 / 页面 | 验证 |
| --- | --- | --- |
| SUCCESS | IMPORT_SUCCESS | scenarios 实际导入终态 |
| PARTIAL_SUCCESS / 行错误 | IMPORT_PARTIAL、任意 CSV | scenarios、business |
| FAILED / INVALID_HEADER | IMPORT_FILE_FAILED | scenarios 文件级失败 |
| 相同文件复用任务 | FILE_DUPLICATE | 相同 ID、duplicateFile、HTTP 200 |
| 不同文件业务行重复 | ROW_DUPLICATE | 第二任务 duplicateRows |
| EXACT / EXACT_MATCH / MATCHED | EXACT | scenarios 逐笔 API |
| 强键 TOLERANCE_MATCH / MATCHED | STRONG_TOLERANCE | 三天边界 |
| 弱键 TOLERANCE_MATCH / MATCHED | WEAK_TOLERANCE | 不同流水号、单人工候选 |
| NO_CANDIDATE / UNMATCHED / NONE | NO_CANDIDATE | scenarios |
| MULTIPLE_CANDIDATES / DUPLICATE / NONE | MULTIPLE_CANDIDATES | 两个人工候选 |
| MANUAL_ALREADY_MATCHED / DUPLICATE / NONE | MANUAL_ALREADY_MATCHED | 强键优先预留 |
| DIRECTION_MISMATCH / SUSPICIOUS / NONE | DIRECTION_MISMATCH | scenarios |
| AMOUNT_MISMATCH / SUSPICIOUS / NONE | AMOUNT_MISMATCH | scenarios |
| TIME_OUT_OF_RANGE / SUSPICIOUS / NONE | TIME_OUT_OF_RANGE | 四天超界 |
| LARGE_AMOUNT | LARGE_AMOUNT、LARGE_AMOUNT_BOUNDARY | 默认阈值 10000.00；实际由服务端返回 |
| 大额未命中 | LARGE_AMOUNT_BELOW | 9999.99；另测规则关闭和 50000.00 阈值 |
| POSSIBLE_DUPLICATE | POSSIBLE_DUPLICATE | 相同账户/方向/金额，不同业务键 |
| 疑似重复窗口外 | DUPLICATE_OUTSIDE_WINDOW | 301 秒、默认窗口 300 秒 |
| FREQUENT_TRANSACTION | FREQUENT_TRANSACTION | 10 分钟内五笔支出 |
| 高频阈值前 | FREQUENT_BELOW | 四笔支出 |
| CONFIRMED | CONFIRMED | 实际审核终态 |
| IGNORED | IGNORED | 实际审核终态 |
| REVIEW_VERSION_CONFLICT | VERSION_CONFLICT | PENDING 时主动提交不一致版本；不是伪造并发编辑 |
| 终态再次决定拒绝 | TERMINAL_DECISION、保存旧版本入口 | 当前后端先检查终态；显示真实 code |
| 匿名 401 | ANONYMOUS_401 | 真实后端请求 |
| REVIEWER 写入 403 | REVIEWER_WRITE_403 | 真实后端请求 |
| ADMIN 决策 403 | ADMIN_DECISION_403 | 真实后端请求 |
| 原四参数与双会话闭环 | GUIDED | accessibility 单步执行、审计统计 |

## 工程能力与证据边界

| 能力 | 工程区入口 / 证据 | 当次验证范围 |
| --- | --- | --- |
| Health | 采集同源 /actuator/health | business 实际 HTTP 200 / UP |
| Prometheus 指标 | 采集同源 /actuator/prometheus | 实际文本指标，非空才标为已采集 |
| Prometheus target | 可配置链接 | 隔离监控 API 的 target UP |
| Grafana | 可配置链接 | 隔离 /api/health HTTP 200；布局资料仍以当前看板为准 |
| Outbox 发布与 ACK | MQ 卡、任务与队列步骤 | 全后端回归和异步真实浏览器终态；机制不能仅从终态推断 |
| MQ 有限重试 | 原 messaging-retry-drill | 独立 finguard-day6 脚本及真实 Testcontainers |
| 死信 | 原 messaging-retry-drill | RETRY_EXHAUSTED / DLQ 测试事实 |
| 消费者幂等 | MQ 卡、后端集成测试 | 全回归；浏览器任务复用不单独证明消费者去重 |
| Redis 缓存/TTL/失效 | Redis 卡、核对步骤 | 全回归；相同统计响应不证明命中 |
| Redis 故障恢复 | 原 redis-outage-drill | 独立 finguard-day6 故障与恢复 HTTP |
| 限流与 Retry-After | 权限/限流卡、隔离命令 | 后端真实 Redis 回归；当次业务栈提高限额用于场景批量验收 |
| 权限 | 401/403 配方 | 真实后端拒绝，按钮状态不是权限证据 |
| 乐观锁 | 版本冲突 / 终态卡 | 实际不同版本 409 与终态拒绝分开 |
| SQL / 压测 | 原性能报告、认证压测步骤 | 历史复现入口；没有新的吞吐/p95 结论 |
| 安全扫描 | 原安全报告、测试入口 | 历史复现入口；本次不是新的安全扫描 |
| CI/CD | 工作流与部署文档 | 复现入口；未推送、未触发新 CI |
| 部署/回滚 | 部署文档 | 复现入口；本次未部署生产/远端服务 |

浏览器不执行容器/数据库命令，不持有对应凭据。当前工程演练的报告链接与“当前服务器已经验证”是不同事实。历史资料遵循报告正文时间、提交和环境。
