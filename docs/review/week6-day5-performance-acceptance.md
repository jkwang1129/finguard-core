# Week 6 Day 5 性能验收记录

日期：2026-08-12

## 结论

Day 5 的性能证据链和最小优化已完成：本地隔离环境回归全绿；新建 Alibaba Cloud ECS 已完成真实数据基线、V11 迁移、健康与 SQL 复核；优化前 ECS 的 25 并发触发了 1.0167% 超时并停止继续升压。V11 后 MySQL 已改为默认排序索引扫描，应用健康且无重启。JMeter 优化后复测未形成有效结论：本地脚本传递的 JWT 在 ECS 返回 401，而 ECS 内部同一容器生成的 JWT 通过 curl 返回 200，因此没有把这组认证失败数据冒充性能结果。

## 环境与发布

- ECS：当天新建的 Ubuntu 22.04、2 vCPU、约 7.2 GiB 可用内存；应用、MySQL、RabbitMQ、Redis、Prometheus、Grafana 均使用保留卷。
- 应用通过仅绑定本机的 SSH 隧道访问；没有新增公网服务端口。
- JMeter 5.6.3 CLI，客户端 Java 21；ECS 应用镜像 Java 17。
- Git 提交：`577ef62030283b63e3dbbdad609c7c1c2c9e4337`；GitHub Actions run `31608078222` 成功完成测试、immutable GHCR 推送及 Linux 启停/回滚生命周期。
- ECS 最终运行上述 SHA 镜像；Flyway 最新版本为 V11，应用容器健康，重启次数为 0。

## 数据库证据

ECS 的合成夹具为 50,000 个导入任务和 50,000 条审计记录，连同原有数据共 50,002 条 `audit_logs`。

优化前 `EXPLAIN ANALYZE` 为 `Table scan on audit_logs` + 排序，实际扫描约 50,002 行，排序/限制阶段约 21.4 ms；在本地同样数据上也复现了全表扫描。

新增不可变迁移 `V11__add_audit_log_default_pagination_index.sql`：

```sql
INDEX idx_audit_logs_created_id (created_at DESC, id DESC)
```

ECS 应用迁移后，`EXPLAIN ANALYZE` 为 `Index scan on audit_logs using idx_audit_logs_created_id`，只取 20 行，实际约 0.057 ms。优化后清理验证：`D5PERF_` 导入任务 0、合成审计记录 0，原有审计记录仍为 2。

## JMeter 与监控

本地隔离 Compose 的 5/10/25/50 并发优化前后均为 0 错误；V11 后吞吐与 P95 均改善，完整 Maven 回归为 379/379，Java 17 Docker 回归也为 379/379。

ECS 优化前 5 并发预热为 836 样本、0 错误、P95 190 ms；10 并发为 2,829 样本、0 错误、P95 361 ms；25 并发为 2,459 样本、25 次读取超时/连接重置、错误率 1.0167%，P95 966 ms，最大 189,252 ms，按安全停止门槛停止继续升压。

V11 后的 ECS 直接业务探针（容器内生成 ADMIN JWT）返回 HTTP 200、总数 50,002；Prometheus 查询显示进程 CPU 约 0.083、Hikari pending 为 0、HTTP 5xx 无数据、审计接口 P95 约 1.5 ms，健康为 `UP`。外部 JMeter 复测使用的令牌传递被截断/换行，返回 401，已停止该复测并清理临时令牌；这组数据不作为性能通过证据。

## 范围与清理

- 未修改已应用的 V10；没有删除 Docker 命名卷、原有业务数据或其他项目资源。
- Day 5 不包含安全扫描、故障注入、TLS/Nginx、Kubernetes 或分布式压测；这些保持在 Day 6/后续范围。
- 临时 JTL、HTML、JMeter 二进制、JWT、SSH 信息和 ECS 地址均未纳入 Git。
