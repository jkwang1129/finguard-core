# FinGuard Core 性能报告

## 1. 结论边界

性能工作只验证审计日志默认分页这一条已实现路径。证据由认证后的 JMeter、Prometheus 和真实 MySQL `EXPLAIN ANALYZE` 组成。它不能外推为整个平台容量、生产 SLA 或峰值吞吐。

V11 的索引访问路径已经被真实 SQL 证明；ECS 上的优化后外部 JMeter 复测因 JWT 传递损坏而全部返回 401，因此不能宣称 ECS 的吞吐或 P95 改善百分比。

## 2. 环境与负载

- Alibaba Cloud ECS：Ubuntu 22.04，2 vCPU，约 7.2 GiB 可用内存；六服务保留卷部署。
- JMeter 5.6.3 CLI，经 SSH 隧道调用受 JWT 保护的 `GET /api/audit-logs`。
- 数据：50,000 条合成导入/审计记录，加原有 2 条，共 50,002 条审计日志。
- 测试只读、可清理；JTL、HTML、JWT、SSH 信息不入库。

## 3. 优化前观测

| 并发 | 样本 | 错误 | P95 |
| ---: | ---: | ---: | ---: |
| 5 | 836 | 0 | 190 ms |
| 10 | 2,829 | 0 | 361 ms |
| 25 | 2,459 | 25（1.0167%） | 966 ms |

25 并发最大响应 189,252 ms，错误为读取超时/连接重置。错误率超过 1% 安全停止门槛，因此没有继续 ECS 升压。

MySQL `EXPLAIN ANALYZE` 显示默认分页执行全表扫描和排序，扫描约 50,002 行，排序/限制阶段约 21.4 ms。

## 4. 最小优化

新增不可变迁移 `V11__add_audit_log_default_pagination_index.sql`：

```sql
INDEX idx_audit_logs_created_id (created_at DESC, id DESC)
```

迁移后 `EXPLAIN ANALYZE` 使用该索引扫描，只读取 20 行，实际约 0.057 ms。应用健康、Flyway V11、直接业务探针 HTTP 200；Prometheus 当时观察到进程 CPU 约 0.083、Hikari pending=0、HTTP 5xx 无数据，审计接口 P95 约 1.5 ms。

## 5. 无效复测与不能声称的内容

优化后外部 JMeter 请求返回 401，而 ECS 容器内生成的同类 JWT 经 curl 请求返回 200。根因定位为 Windows/隧道传递中的 Token 截断或换行，不是业务响应。故：

- 这组 401 不能进入延迟、吞吐或错误率对比；
- 不能写“ECS 性能提升 N%”或“支持 N 并发”；
- 可以写“通过 EXPLAIN ANALYZE 将默认分页从全表扫描/排序改为复合索引扫描”；
- 如需完成对比，必须先用目标 JMeter 请求做单请求 HTTP 200 探针，再开始负载。

## 6. 清理与复现

合成数据清理后 `D5PERF_` 导入任务和审计记录均为 0，原有 2 条审计保留。复现入口和 SQL/JMX 见 `performance/`；完整原始记录见[Week 6 Day 5 验收](review/week6-day5-performance-acceptance.md)。
