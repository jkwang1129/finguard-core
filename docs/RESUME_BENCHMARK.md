# FinGuard Core：简历量化实测

2026-11-19 至 20（北京时间），服务源码 `504e870`。使用本地 Docker 隔离环境、合成数据和真实 REST API，未使用演示环境的存量数据，也未测试生产流量。测试机为 Core Ultra 5 225H，Docker VM 可见 14 vCPU、约 15.4 GiB 内存；应用容器限制 768 MiB，Java 17、MySQL 8.4.10、RabbitMQ 4.3.4、Redis 8.2.8，Node.js 24.14.1。导入消费者为单实例、预取 1；Outbox 调度间隔默认 1 秒，上传限流仅在测试环境调为每分钟 200 次。

## 实测结果

| 指标 | 测法与结果 | 证据边界 |
| --- | --- | --- |
| CSV 导入 | 1,000 / 5,000 / 10,000 行各测 3 轮，共 48,000 行、0 行失败。单文件 10,000 行中位 **965 ms，10,363 行/秒** | 从开始上传到首次读到成功终态，含调度、落库及 150 ms 间隔轮询；不含 CSV 生成。非持续吞吐或生产容量 |
| 文件与交易去重 | 同字节文件改名重传，复用原任务；倒序文件混入 3 条新交易，拦截 60 条重复、保留 3 条新增；4 个文件并发提交、各含相同 60 个交易键，最终只新增 60 条，拦截 180 条；累计拦截 **240/240** 个重复行 | 是并发提交、单消费者处理。240 不含被文件级去重挡住的重传 |
| 对账结果 | 精确匹配、容差匹配、无候选、金额不匹配、多候选各 20 条，**100/100** 与预设结果一致；前两类额外核对匹配交易 ID | 容差组用不同流水号、相同金额和时间触发；未测金额、时间阈值边界，不代表真实对账准确率或风控召回率 |
| 并发审核 | 3 个任务各发起 20 个并发请求，使用同一任务版本；每轮 **1 成功、19 个 HTTP 409**，版本只加 1，仅胜出的决策备注落库 | 同一审核账号，无多实例或大规模并发结论 |
| Outbox 故障恢复 | RabbitMQ 停机期间提交 1 个导入任务，观察到任务 `PENDING`、Outbox `RETRY`、发布失败计数为 2。强制停止应用（退出码 137），先恢复 RabbitMQ 再恢复应用，事件变为 `SENT`，任务变为 `SUCCESS`，只写入 1 条交易 | 一次组合故障演练；不是 RTO、零丢失保证或任意崩溃窗口验证 |
| 重复消息消费 | 向真实导入交换机手动重放已完结任务的同一消息 5 次，主队列投递及 ACK 均增加 5，重试队列和死信队列计数未增加；交易数、审计条数、任务计数及完成时间均未变 | 验证终态重复投递，不是 ACK 丢失、消费中断或 Broker 自动重投测试 |
| 审计首页 SQL | 同一批 **50,026** 条审计记录，`IGNORE INDEX` 时全表扫描 50,026 行并排序；刷新统计信息后，正常 SQL 无提示选用 `idx_audit_logs_created_id`，索引扫描读取 **20** 行 | 仅针对无筛选、按 `created_at DESC, id DESC` 排序的首页明细查询，不含分页 `COUNT(*)`、深分页或端到端接口延迟 |

导入的 2 轮预热（1,000 行/轮）不计入下表。每档仅 3 次，固定调度及轮询开销占比不小，不从表中推断数据量增长时的性能趋势。

| 单文件行数 | 3 轮耗时（ms） | 中位耗时（ms） | 按中位耗时折算（行/秒） |
| ---: | --- | ---: | ---: |
| 1,000 | 960 / 796 / 1,121 | 960 | 1,042 |
| 5,000 | 1,181 / 1,110 / 1,114 | 1,114 | 4,488 |
| 10,000 | 1,331 / 953 / 965 | 965 | 10,363 |

## 简历可直接使用

**FinGuard Core｜交易对账与异常审核系统｜Java 后端开发（独立项目）**

面向交易流水核对，开发 CSV 异步导入、自动对账、异常审核与审计追踪。以下为本地合成数据验证结果。

- **导入与幂等：** 用 SHA-256 文件指纹和交易业务唯一键防止重复入库；单文件 1 万行端到端导入 3 轮中位约 1.0 秒，乱序补录及并发提交测试共拦截 240/240 条重复交易。
- **异步任务可靠性：** 通过 Transactional Outbox 将任务与事件同事务落库，结合 RabbitMQ 发布确认、手动 ACK 与任务终态判定；验证 Broker 不可用且应用进程被强制停止后任务可恢复，重放同一消息 5 次未产生重复交易。
- **对账与审核一致性：** 实现精确/容差匹配及异常分类，5 类预标注样本共 100/100 符合预期；通过状态约束和版本号乐观锁处理并发审核，3 轮各 20 个同版本请求，每轮仅 1 次决策生效。
- **审计查询优化：** 用 `EXPLAIN ANALYZE` 对照验证降序复合索引 `(created_at DESC, id DESC)`，约 5 万条审计数据下，首页明细查询从全表扫描并排序变为索引读取 20 行。

如果确实是在某家公司实习期间承担的工作，可以把真实公司和岗位写在「实习经历」下；否则保留「项目经历」，不要把合成数据写成线上交易或把单次演练写成可靠性 SLA。

## 复现与原始证据

1. 使用独立 Compose 项目和容器名前缀，提供专用的数据库、Broker、Redis、JWT 和测试账号配置，应用绑定本地 `18080`。测试账号的用户名以 `bench-` 开头。不要对现有演示环境或生产环境执行以下操作。
2. 在 PowerShell 中启动本地测试服务，并向当前进程载入测试账号环境变量：

   ```powershell
   docker compose --env-file target/resume-benchmark/compose.env -p fg-resume-1119 up -d --build --wait app
   Get-Content target/resume-benchmark/compose.env | ForEach-Object {
     $name, $value = $_ -split '=', 2
     if ($name -match '^FINGUARD_AUTH_BOOTSTRAP_(ADMIN|REVIEWER)_(USERNAME|PASSWORD)$') {
       [Environment]::SetEnvironmentVariable($name, $value, 'Process')
     }
   }
   node performance/scripts/run-resume-benchmark.mjs
   ```

3. 停止隔离 RabbitMQ 后运行同一脚本的 `fault-submit`，检查任务和 `outbox_events`；等到发布进入 `RETRY` 后强停隔离应用，依次恢复 Broker 与应用。运行 `fault-check <jobId> <accountId>` 确认任务终态与交易总数。以同一个 `JobRequestedMessage` 向 `finguard.import.exchange` 的 `import.requested` 路由键发布 5 次，分别对比 Broker ACK 和数据库结果。
4. 在隔离 MySQL 执行 `performance/sql/seed-audit-log-performance-data.sql`，运行 `ANALYZE TABLE audit_logs`，对 `performance/sql/explain-audit-log-query.sql` 的明细查询分别测试禁用 `idx_audit_logs_created_id` 和无索引提示的计划。初次批量灌数后曾观察到未加提示仍走全表扫描，因此记录统计信息刷新后的计划。
5. 本机原始输出保留在被 Git 忽略的 `target/resume-benchmark/`：`api-results.jsonl`、`outbox-state.txt`、`outbox-recovered.json`、`mq-replay.json` 和 `sql-comparison.txt`。`compose.env` 含本地测试凭证，不随报告分享。
