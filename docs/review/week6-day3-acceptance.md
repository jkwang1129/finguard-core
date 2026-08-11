# Week 6 Day 3 验收记录

## 结论

Week 6 Day 3 的应用指标、Prometheus 采集、Grafana 自动配置和六服务闭环均已通过真实验收。验收使用独立 Compose 项目、容器前缀、宿主机端口和临时卷，不复用或删除开发环境数据。

## 自动化与静态校验

- 最终指标与 Actuator 安全聚焦测试：10/10，0 failures、0 errors、0 skipped。
- 完整 `mvn -B -ntp clean test`：378/378，0 failures、0 errors、0 skipped。
- `docker compose config --quiet`：通过。
- Prometheus `promtool check config`：通过。
- Grafana dashboard JSON：`uid=finguard-overview`，9 个面板且 ID 唯一。
- GitHub Actions：actionlint 1.7.12 通过。
- 应用镜像：约 161.01 MiB；运行用户 UID/GID 10001；Java 17.0.19；镜像内不存在 Maven 或项目源码。

## 独立六服务验收

环境使用 Compose 项目 `finguard-day3-acceptance` 和容器前缀 `finguard-day3`，启动 app、MySQL、RabbitMQ、Redis、Prometheus、Grafana 六个服务。最终结果：

- 6/6 服务为 healthy，MySQL 的 Flyway 最新成功版本为 V10。
- 一次性 ADMIN 通过真实登录接口取得 JWT。
- 真实账户与人工交易创建成功。
- `curl.exe -F` multipart CSV 导入完成为 `SUCCESS`，成功行数为 1。
- 引用不存在账户的 CSV 导入完成为 `FAILED`，失败行数为 1。
- 异步对账完成为 `COMPLETED`。
- 向导入 exchange 发送事件类型不匹配的持久 JSON 消息，消息被稳定分类并增加消费失败指标，没有改变 ACK、重试或业务状态语义。

Prometheus API 的重启前实测值：

| 查询 | 结果 |
|---|---:|
| `up{job="finguard-core"}` | 1 |
| `sum(finguard_import_jobs_completed_total)` | 2 |
| `sum(finguard_reconciliation_processing_seconds_count)` | 1 |
| `sum(finguard_messaging_consumer_failures_total)` | 1 |

同时确认 `http_server_requests_seconds_count`、`jvm_memory_used_bytes` 和 `hikaricp_connections` 均存在。Grafana 健康接口返回数据库正常，自动配置的数据源指向 `http://prometheus:9090`，`finguard-overview` 仪表盘包含 9 个面板。

匿名访问 `/actuator/prometheus` 成功；匿名访问 `/actuator/env` 返回 401，携带有效 JWT 后返回 404，证明该端点没有被暴露。重启应用并等待重新抓取后，PromQL `changes(process_start_time_seconds{job="finguard-core"}[5m])` 仍可观察到进程变化，证明 Prometheus 卷保留了重启前历史，而进程内 Counter 按预期重新开始。

## 清理

验收结束后执行独立项目的 `down -v --remove-orphans`，删除 6 个验收容器、独立网络和 MySQL/RabbitMQ/Redis/Prometheus/Grafana 5 个临时卷。一次性 JWT 密钥和服务凭据只存在于验收脚本进程环境，未写入受版本控制文件。进入验收前已存在的开发容器和数据保持不变。
