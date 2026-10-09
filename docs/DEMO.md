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

### 交互式实时演示

六服务栈全部 healthy 后，打开同源页面：<http://127.0.0.1:8080/demo/index.html>。页面调用当前 Spring Boot 应用的真实 API，并将服务端返回的任务状态、对账结果、审核项和运行记录逐步显示出来；这不是纯前端模拟。

请先准备两个不同的本地演示账号：一个 ADMIN、一个 REVIEWER。可用首次用户 Bootstrap 配置创建缺少的账号，或使用现有认证设置。Compose / `application.yml` 支持以下变量；只在本地受保护的环境配置中设置凭据，不要把值提交到仓库或粘贴到文档：

- `FINGUARD_AUTH_BOOTSTRAP_ENABLED`
- `FINGUARD_AUTH_BOOTSTRAP_ADMIN_USERNAME`
- `FINGUARD_AUTH_BOOTSTRAP_ADMIN_PASSWORD`
- `FINGUARD_AUTH_BOOTSTRAP_REVIEWER_USERNAME`
- `FINGUARD_AUTH_BOOTSTRAP_REVIEWER_PASSWORD`

首次创建账号时启用 Bootstrap 并配置两组不同用户名和密码，然后启动应用；账号可登录后关闭 Bootstrap 并从环境配置中移除四项用户名/密码，参见[部署手册的首次用户说明](DEPLOYMENT.md#4-首次用户与访问)。在页面的 ADMIN 和 REVIEWER 区域分别登录对应账号。JWT 和凭据只保存在当前页面内存中。

开始任何写入前，勾选“我已了解演示会写入当前数据库”。逐步演示通过“生成并预览场景”创建唯一批次，显示将创建的数据，并逐个执行步骤。刷新需要重新登录；演示数据会保留在数据库。原四个调参输入及当前默认值见下文“完整控制台工作区”。

交互页面之外，仍可按下文继续用 Swagger UI 手动逐步调用，也可运行隔离的 Week 6 Day 7 验收脚本做一键证明；两种流程都保留。

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

## 完整控制台工作区

`/demo/index.html` 现提供逐步场景、账户、交易、CSV 导入、对账、审核、审计统计与工程证据八个导航入口。身份来自 `/api/auth/me`；两个会话只保存在内存中，刷新后重新认证。历史任务可按列表或 ID 找回。

账户与交易支持后端已有全部筛选及分页。ADMIN 负责业务写入，REVIEWER 负责审核决定；CSV 交易只读。上传保留原始字节，预览不改变文件。202 是任务受理，只有 GET 查询到终态才表示处理结束；轮询超时可继续 GET，不会自动重发创建。

逐步演示保留人工金额、CSV 匹配金额、-4 至 +4 天偏移、风险行金额四个输入；当前默认值为 100.00、100.00、0、12000.00，本地业务时间采用 Asia/Shanghai，默认锚点为十天前，避免 +4 天配方触发 CSV 的未来时间限制。新场景生成唯一 DEMO 账户，可预览全部写入再执行每一步。原页面的批次重置行为由“生成并预览场景”替代，不删除数据库数据。任意 CSV 与常规管理通过相应工作区操作。

场景区提供完整对账原因、三条规则和边界、文件与业务行去重、两种决定、版本冲突、终态拒绝和权限拒绝。预期是配方条件；实际事实始终来自 API。风险规则默认配置仅作说明，非默认阈值或关闭规则可能返回无命中。

工程区采集同源 health/prometheus，提供可配置监控链接和维护者隔离演练步骤。浏览器不启动 Docker，不持有服务凭据。旧报告注明原时间/环境；没有当次证据的演练标为未验证。覆盖与当前验收结果见 `docs/DEMO_COVERAGE.md` 和 `docs/review/2026-10-09-complete-demo-console-acceptance.md`。
