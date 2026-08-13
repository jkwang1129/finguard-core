# FinGuard Core 监控与排障

## 1. 组件与入口

| 组件 | 默认本机入口 | 用途 |
| --- | --- | --- |
| Spring Boot | `http://127.0.0.1:8080/actuator/health` | 应用及依赖健康 |
| Spring Boot | `http://127.0.0.1:8080/actuator/prometheus` | 指标抓取 |
| Prometheus | `http://127.0.0.1:9090/api/v1/targets` | target 状态 |
| Grafana | `http://127.0.0.1:3000/api/health` | UI/数据源/仪表盘 |

Actuator 只暴露 `health,prometheus`。`env`、`beans`、`heapdump` 等端点不开放。Linux 上这些入口都只绑定回环地址。

## 2. 业务指标契约

```text
finguard_import_jobs_completed_total{outcome="success|partial_success|failed"}
finguard_reconciliation_processing_seconds_*{outcome="completed|failed"}
finguard_messaging_consumer_failures_total{flow="import|reconciliation",reason="..."}
```

- 导入 Counter 只在新终态事务提交后增加，重复消息不重复计数。
- 对账 Timer 测量实际处理并区分完成/失败。
- 消费失败 Counter 统计处理尝试，不等同于唯一失败消息数。
- 标签只允许固定 outcome/flow/reason；禁止任务 ID、用户 ID、文件名、消息 ID、JWT、异常文本或堆栈。

Grafana 的 `FinGuard Core Overview` 由仓库 provisioning 自动创建，共 9 个面板，覆盖 target、HTTP 请求/5xx/P95、JVM、HikariCP、导入、对账与消息失败。

## 3. 常用 PromQL

```promql
up{job="finguard-core"}
sum(rate(http_server_requests_seconds_count{application="finguard-core"}[5m]))
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
sum by (outcome) (finguard_import_jobs_completed_total)
histogram_quantile(0.95, sum by (le, outcome) (rate(finguard_reconciliation_processing_seconds_bucket[5m])))
sum by (flow, reason) (finguard_messaging_consumer_failures_total)
```

查询结果为空不一定是故障：Counter 需要业务事件，速率和分位数需要时间窗口内样本。先生成一条受控业务流，再确认 scrape 时间和标签。

## 4. 告警建议

项目未配置实际通知渠道，以下是建议规则而非已部署 SLA：

- `up{job="finguard-core"} == 0` 持续 2–5 分钟；
- HTTP 5xx 比率或 P95 持续超过团队设定的基线；
- Hikari pending 持续大于 0；
- 消费失败 Counter 快速增长或任一 DLQ 非空；
- 导入/对账失败终态增长；
- 应用频繁重启或依赖健康持续 DOWN。

## 5. 排障顺序

1. `docker compose ps` 确认哪个服务 unhealthy/exited。
2. 读取目标服务有限日志，如 `docker compose logs --tail=200 app`。
3. 查询 `/actuator/health` 和 Prometheus targets，区分应用、依赖和抓取问题。
4. 用真实登录和最小只读 API 判断是认证、应用还是数据库路径。
5. 检查 RabbitMQ queue ready/unacked/DLQ，Redis 使用带认证 PING，MySQL 查 Flyway 和目标业务事实。
6. 恢复后重复 health、业务查询和 target UP；不要只看容器进程存在。

Redis 故障时 health 为 503 但登录/统计可能因 fail-open/回源仍为 200；这两组信号必须同时解释。RabbitMQ 演练必须排除竞争消费者，否则受控消息可能被常驻实例抢走。

相关证据：[Day 3 监控验收](review/week6-day3-acceptance.md)、[Day 6 故障复盘](review/week6-day6-fault-drills.md)。
