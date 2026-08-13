# FinGuard Core 演示手册

## 1. 演示目标

用 12–15 分钟展示一条完整且可核验的后端链路：登录/RBAC → 账户与交易 → CSV 异步导入 → 自动对账 → 风险审核 → 审计与统计 → Prometheus/Grafana。演示只使用一次性数据和占位凭据，不展示 `.env`、JWT、密码、主机地址或内部日志全文。

## 2. 演示前检查

推荐使用本地六服务或 Day 7 隔离验收环境。开始前确认：

```powershell
docker compose ps
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:9090/api/v1/targets
Invoke-RestMethod http://127.0.0.1:3000/api/health
```

预期六服务 healthy，应用 `status=UP`，Prometheus 的 `finguard-core` target 为 `UP`。打开 Swagger UI：`http://127.0.0.1:8080/swagger-ui/index.html`。

如只需要一键证明而不做现场讲解，执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance/invoke-week6-day7-acceptance.ps1 -PreflightOnly
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance/invoke-week6-day7-acceptance.ps1
```

脚本只操作 `finguard-day7` 隔离项目，结束后自动清理。

## 3. 演示顺序

### 场景 A：认证与权限（2 分钟）

1. 匿名调用 `GET /api/accounts`，展示 401。
2. 使用演示 ADMIN 登录，复制返回的 Token 到 Swagger `Authorize`；不要把 Token 留在截图。
3. ADMIN 调用账户查询，展示 200。
4. 切换 REVIEWER，尝试 `POST /api/accounts`，展示 403；读取账户仍为 200。

讲解点：401 是未建立身份；403 是身份有效但权限不足。JWT 负责身份声明，Spring Security 路由矩阵负责授权。

### 场景 B：账户、交易和异步导入（3 分钟）

1. ADMIN 创建账户：

```json
{
  "accountNo": "DAY7-DEMO",
  "accountName": "Day 7 Demo",
  "accountType": "BANK",
  "currency": "CNY"
}
```

2. 创建一条用于匹配的人工交易，金额 `88.00`、方向 `INCOME`，时间与样例 CSV 的匹配行一致。
3. 上传 `sample-data/demo-import.csv`，展示 `202 Accepted`、`Location` 和 `importJobId`。
4. 轮询 `GET /api/import-jobs/{id}` 至 `SUCCESS`；再次上传同一文件，展示返回原任务而不产生第二份事实。

讲解点：HTTP 202 只表示受理；文件 SHA-256、数据库唯一键、Outbox 和消费者终态幂等共同保护重复。

### 场景 C：对账、风险与审核（4 分钟）

1. ADMIN 对该导入任务调用 `POST /api/reconciliation-jobs`。
2. 轮询到 `COMPLETED`，查询逐笔结果：`88.00` 行匹配人工交易，`10000.00` 行触发大额风险。
3. 查询审核任务，找到 `LARGE_AMOUNT` 对应的 `PENDING version=0` 任务。
4. ADMIN 尝试决策，展示 403；REVIEWER 携带当前 version 提交 `CONFIRMED` 或 `IGNORED`，展示终态和 `version=1`。
5. 再次用旧 version 提交，展示 409，说明条件更新防止并发覆盖。

讲解点：对账结果、风险命中、审核任务和任务终态同事务提交；审核人只从已验签 JWT subject 取得，不能由请求体伪造。

### 场景 D：审计、缓存与监控（3 分钟）

1. ADMIN 查询 `/api/audit-logs`，展示上传、对账完成和审核决定的白名单事件。
2. REVIEWER 查询审计，展示 403。
3. ADMIN/REVIEWER 查询 `/api/statistics/overview`；说明 Redis 使用固定 key 和 60 秒 TTL，失效后回源 MySQL。
4. 在 Prometheus 查询 `up{job="finguard-core"}` 和三组 `finguard_*` 指标。
5. 打开 Grafana `FinGuard Core Overview`，展示 target、HTTP、JVM/HikariCP 和业务面板。

讲解点：审计摘要和指标标签都不能包含 JWT、CSV 原文、用户/任务 ID 或异常文本。

## 4. 常见问题与备用方案

- 导入仍是 `PENDING/PROCESSING`：检查 RabbitMQ queue/unacked 和应用有限日志，不要反复上传。
- PromQL 暂无数据：先完成一次业务流，再等待一个 scrape interval；Counter 没有事件时为空是正常的。
- REVIEWER 决策 409：重新获取任务详情和当前 version，说明另一个请求已经改变状态。
- Swagger 状态丢失：重新登录并只在浏览器内设置 Token；不要把 Token 放入脚本、截图或聊天。
- 现场环境异常：展示最后一次[Week 6 最终复盘](review/week6-review.md)中的验收表，同时明确它是历史证据，不冒充当前在线状态。

## 5. 演示后清理

隔离验收脚本会自动清理。如果手工使用普通开发栈，只删除明确创建的 `DAY7-DEMO` 夹具；不要为了清理演示数据执行 `docker compose down -v`。结束后退出 Swagger 授权、清除会话环境变量，并确认没有把 Token 或密码写入终端历史、截图和仓库。
