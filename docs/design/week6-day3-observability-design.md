# Week 6 Day 3 可观测性设计

## 1. 目标

Day 3 在不改变既有业务状态、事务、消息 ACK/NACK 或权限语义的前提下，为 FinGuard Core 建立一套最小但可真实运行的监控闭环：

```text
应用运行与业务事件
  -> Micrometer MeterRegistry
  -> /actuator/prometheus
  -> Prometheus 定时抓取
  -> Grafana 自动加载数据源和仪表盘
```

它需要回答五类问题：

1. HTTP 请求量、错误率和 P95 延迟是否异常；
2. JVM 内存是否持续升高；
3. HikariCP 连接池是否接近耗尽；
4. CSV 导入的成功、部分成功和失败数量如何变化；
5. 对账耗时和 RabbitMQ 消费失败次数是否增加。

Day 3 的输出将作为 Day 5 压测优化和 Day 6 故障演练的共同证据来源。

## 2. 当前基线

- 应用为 Spring Boot 3.5.16 / Java 17，已包含 Actuator，但只暴露 `health`。
- Week 6 Day 2 已提供非 root 应用镜像、应用/MySQL/RabbitMQ/Redis 四服务 Compose 和真实 GitHub Actions CI。
- 当前没有 Prometheus Registry、自定义 Meter、Prometheus/Grafana 容器、采集配置或仪表盘。
- Flyway 最新版本为 V10；Day 3 不修改数据库结构。
- 异步导入和对账使用至少一次投递、手动 ACK、有限重试与 DLQ；业务完成指标不能因重复投递而重复计数。

## 3. 指标契约

### 3.1 框架指标

| 目的 | Micrometer 指标 | Grafana 展示 |
|---|---|---|
| HTTP 请求 | `http.server.requests` | 吞吐、5xx 错误率、P95 |
| JVM 内存 | `jvm.memory.used` / `jvm.memory.max` | heap/non-heap 使用量与使用率 |
| 数据库连接池 | `hikaricp.connections.*` | active、idle、pending、max |
| 应用抓取状态 | Prometheus `up` | 当前 target 是否可抓取 |

只对 `http.server.requests` 启用有界直方图。HTTP P95 使用 Prometheus histogram bucket 聚合，不使用只在单实例内有意义的客户端 percentile。面板查询排除 `/actuator/**`，避免 Prometheus 自身抓取流量污染业务请求趋势。

### 3.2 导入终态 Counter

- Micrometer 名称：`finguard.import.jobs.completed`
- Prometheus 名称：`finguard_import_jobs_completed_total`
- 标签：`outcome=success|partial_success|failed`
- 含义：本应用进程运行期间，新提交的导入任务终态迁移次数。

记录规则：

1. `SUCCESS`、`PARTIAL_SUCCESS`、`FAILED` 数据库状态更新成功后发布内部事件；
2. 由 `AFTER_COMMIT` 事务事件监听器增加 Counter；
3. 事务回滚不计数；
4. `ALREADY_COMPLETED`、终态条件更新返回 0 和 ACK 丢失后的重复投递不计数；
5. Counter 不是数据库当前任务总量，应用重启后由 Prometheus 保存历史，应用不扫描全表恢复 Counter。

### 3.3 对账处理 Timer

- Micrometer 名称：`finguard.reconciliation.processing`
- Prometheus 名称：`finguard_reconciliation_processing_seconds_*`
- 标签：`outcome=completed|failed`
- 含义：消费者调用一次真实对账处理事务所花费的时间。

记录规则：

1. 消息契约验证通过后、进入事务 Service 前开始计时；
2. `PROCESSED` 与 `RECOVERED` 记录为 `completed`；
3. 业务异常或系统异常记录为 `failed`，异常继续按原逻辑向上抛出；
4. `ALREADY_COMPLETED` 是终态幂等空操作，不记录新 Timer；
5. 只为该 Timer 配置明确桶边界，Grafana 用 `histogram_quantile` 展示 P95。

### 3.4 消息消费失败 Counter

- Micrometer 名称：`finguard.messaging.consumer.failures`
- Prometheus 名称：`finguard_messaging_consumer_failures_total`
- 标签：
  - `flow=import|reconciliation`
  - `reason=invalid_message|invalid_retry_attempt|task_not_found|business_failed|transient_failure|retry_exhausted`
- 含义：被消费者分类并进入失败处理路径的次数，不是唯一消息数。

每次失败处理尝试只增加一次。若可靠转发失败并使原消息重新入队，下一次投递再次失败会形成新的失败处理次数，这是有意保留的故障强度信号。该计数不改变转发、ACK、NACK、重试或 DLQ 行为。

### 3.5 标签基数约束

禁止把以下内容作为标签：

- 用户 ID、任务 ID、交易 ID、消息 ID；
- 用户名、文件名、外部流水号；
- JWT、IP 原文、异常消息、堆栈；
- 原始 URL 或任意数据库值。

业务指标只使用代码内枚举映射出的固定标签。指标 `description` 可以说明语义，但不能包含运行时业务数据。

## 4. 应用设计

新增 `observability` 包：

```text
observability/
  FinGuardMetrics.java
  ImportJobTerminalEvent.java
  ReconciliationMetricOutcome.java
```

`FinGuardMetrics` 负责：

- 注册并更新三个业务 Meter；
- 将 Java 枚举稳定映射为小写标签；
- 在 `AFTER_COMMIT` 收到导入终态事件后增加 Counter；
- 创建/结束对账 `Timer.Sample`；
- 记录有限枚举的消息消费失败次数。

导入事务 Service 只在确实完成状态迁移的位置发布 `ImportJobTerminalEvent`。指标监听器与业务写入解耦，指标异常不得参与业务事务，也不得影响数据库提交结果。

对账 Handler 包围事务 Service 调用以计时，因为 Spring 事务代理会在方法返回 Handler 前完成提交、在异常返回前完成回滚。Listener 在既有失败分类分支调用消息失败 Counter，随后保持原有转发和 ACK/NACK 顺序。

## 5. Actuator 与安全

应用只暴露：

```yaml
management.endpoints.web.exposure.include: health,prometheus
```

安全规则只匿名放行：

- `GET /actuator/health`
- `GET /actuator/prometheus`

`env`、`beans`、`configprops`、`heapdump`、`threaddump` 等端点不进入 exposure，不能通过“已认证”绕过未暴露状态。应用、Prometheus 与 Grafana 的宿主机端口都只绑定 `127.0.0.1`；Day 4 再确定真实 Linux 主机上的防火墙或反向代理策略。

## 6. Prometheus 与 Grafana

### 6.1 Prometheus

- 镜像：`prom/prometheus:v3.7.3`，使用显式版本；
- 内网目标：`app:8080`；
- 路径：`/actuator/prometheus`；
- 固定抓取间隔：`15s`；
- 配置文件只读挂载；
- 数据写入命名卷；
- 宿主机端口 `127.0.0.1:9090`；
- 健康检查使用 `/-/healthy`。

### 6.2 Grafana

- 镜像：`grafana/grafana:12.3.1`，使用显式版本；
- 数据源通过 provisioning 指向 `http://prometheus:9090`；
- 仪表盘通过 provisioning 自动加载版本化 JSON；
- 管理员用户名/密码由环境变量注入，仓库只保存占位值；
- 数据写入命名卷；
- 宿主机端口 `127.0.0.1:3000`；
- 健康检查使用 `/api/health`。

Grafana 仪表盘至少包含：

1. Prometheus target 状态；
2. HTTP 吞吐；
3. HTTP 5xx 错误率；
4. HTTP P95；
5. JVM heap 使用量/上限；
6. HikariCP active/idle/pending/max；
7. 导入终态增量，按 outcome 分组；
8. 对账处理次数与 P95；
9. 消息消费失败增量，按 flow/reason 分组。

## 7. 配置与运行边界

Compose 从四服务扩展为六服务：

```text
mysql/rabbitmq/redis healthy
  -> app healthy
  -> prometheus healthy
  -> grafana healthy
```

Prometheus 依赖 app 健康；Grafana 依赖 Prometheus 健康。监控端口、Grafana 管理员占位凭据和数据卷行为必须写入 `.env.example` 与 README。日常 `docker compose down` 保留数据卷，只有用户明确确认数据可删除时才使用 `down -v`。

CI 继续只启动 MySQL、RabbitMQ、Redis 来执行测试和构建应用镜像；Day 3 的 Prometheus/Grafana 配置通过专用校验命令和真实本地六服务验收验证，不把 CI 扩展成长期运行监控系统。

## 8. 测试矩阵

### 8.1 指标单元测试

- 三类导入终态映射正确；
- 消息 flow/reason 固定映射正确；
- 对账 completed/failed Timer 的 count 与 duration 增加；
- 指标只包含允许的标签键和值。

### 8.2 事务与幂等测试

- 导入终态事件随真实事务提交后 Counter 增加一次；
- 回滚事务不增加 Counter；
- 终态重复投递返回 `ALREADY_COMPLETED` 后不增加 Counter；
- 重试耗尽条件更新只有首次成功时发布事件。

### 8.3 HTTP 与安全测试

- 匿名 `/actuator/prometheus` 返回 Prometheus 文本；
- 内容包含 HTTP/JVM/HikariCP 和业务指标名；
- 匿名 `/actuator/health` 继续返回 `UP`；
- `/actuator/env` 等未暴露端点返回 404；
- 业务接口匿名 401、越权 403 不回退；
- 指标文本不含密码、JWT、任务 ID 或异常内容。

### 8.4 配置与真实闭环

- `docker compose config` 通过；
- `promtool check config` 通过；
- Grafana provisioning YAML 和 dashboard JSON 可解析；
- 完整 `mvn clean test` 通过且无跳过；
- 应用镜像重新构建并保持非 root/Java 17；
- 独立六服务环境全部 healthy；
- Prometheus target 为 `UP`；
- Grafana 自动出现数据源和仪表盘；
- 真实导入、对账和失败样例产生符合契约的指标增量；
- 应用重启后 Prometheus 已采集的历史仍可查询。

## 9. 失败路径

| 故障 | 预期行为 |
|---|---|
| Prometheus 停止 | 应用业务继续运行，只有采集出现空档 |
| Grafana 停止 | Prometheus 继续采集，恢复后可查询历史 |
| `/actuator/prometheus` 不可抓取 | target 为 `DOWN`，不放宽其他 Actuator 端点 |
| 指标记录代码异常 | 不得回滚或改变已提交业务；测试中避免引入可抛出业务异常的动态标签逻辑 |
| RabbitMQ 消费失败 | 原有重试/DLQ/ACK 语义不变，同时增加失败处理次数 |
| 重复消息 | 业务终态 Counter 不重复；失败尝试 Counter 仍按实际处理尝试记录 |
| 应用重启 | 进程内 Counter 从零开始，Prometheus 保留已抓取历史，仪表盘使用 rate/increase 展示 |

## 10. 验收与清理

真实验收使用独立 Compose project name、独立命名卷和测试凭据。结束时只删除本次创建的六服务容器、网络、卷、临时镜像、测试数据和消息；不得删除用户现有开发卷。

完成前必须执行：

- 聚焦测试；
- 完整 `mvn clean test`；
- Compose/Prometheus/Grafana 配置校验；
- 应用镜像构建与内容检查；
- 真实六服务业务/指标闭环；
- 敏感信息检查；
- Day 4～Day 7 范围审计；
- `git diff --check`；
- 最终工作区审查。

## 11. Day 4 交接边界

Day 3 只保证本地/Compose 内的监控闭环。Day 4 才处理镜像发布、真实 Linux 主机、远程访问、防火墙、启动停止、升级回滚和部署故障记录。Day 3 不把“本地六服务运行成功”描述为“已完成生产部署”。

## 12. 回滚

回滚 Day 3 时移除 Prometheus Registry、指标类和接入点、Actuator prometheus 暴露/白名单、Prometheus/Grafana Compose 服务、配置、仪表盘、测试和文档。只删除明确属于 Day 3 的监控容器、网络和临时卷；不修改 V1～V10，不删除已有业务数据，不回退 Week 6 Day 1/Day 2 或 Week 1～Week 5 能力。
