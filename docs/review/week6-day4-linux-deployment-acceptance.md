# Week 6 Day 4：Linux 部署验收记录

日期：2026-08-12

## 1. 结论与环境边界

Day 4 的版本化交付物、不可变镜像发布和可重复部署链路已经实现，并在隔离环境与一台经用户授权的持久 Alibaba Cloud ECS 上完成验证：

- 本机 Docker Desktop Linux Engine：完成六服务业务与监控闭环、停机恢复和数据卷保持验证；
- GitHub-hosted Ubuntu runner：完成真实 CI、私有 GHCR 发布、指定 SHA 拉取、六服务启动、状态检查、停机恢复和资源清理。
- 持久 ECS：完成主机基线、SSH 隧道业务验收、Bootstrap 收口、保留卷生命周期和两个已发布 SHA 的升级/回滚。

本记录严格区分已验证事实和待补证事实；服务器地址、账号、口令和 Token 不写入仓库。

## 2. 交付物检查

| 项目 | 结果 |
|---|---|
| 应用镜像 | 仅接受 `ghcr.io/jkwang1129/finguard-core:<40 位 Git SHA>` |
| 发布门禁 | `main` push、测试 job 成功后才运行，job 权限为 `contents: read`、`packages: write` |
| Linux Compose | 六服务、应用无 `build`、依赖服务无宿主机端口 |
| 访问面 | app/Prometheus/Grafana 仅绑定 `127.0.0.1` |
| 持久化 | MySQL、RabbitMQ、Redis、Prometheus、Grafana 共 5 个命名卷 |
| 运行保护 | 六服务健康检查、`unless-stopped`、停止宽限期、`json-file` 10 MiB × 3 |
| 凭据 | `.env.linux` 和 `.deploy-state` 被 Git/Docker build context 忽略；环境文件要求 600/400 |
| 运维脚本 | preflight、deploy、status、stop、rollback 以及共享校验已提供 |

## 3. 本地完整回归与隔离运行验收

### 3.1 静态与构建

- `mvn -B -ntp clean package`：378 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS；
- `docker compose config --quiet`：Linux Compose 解析通过；
- actionlint、ShellCheck、`sh -n`、Prometheus `promtool`、Grafana dashboard JSON 和 `git diff --check`：通过；
- 应用镜像约 169 MB，以用户 `finguard` 运行，Java 17，不包含 Maven 或源码，OCI source/revision 标签正确；
- 敏感字面量扫描未发现 PAT、私钥或云访问密钥。

### 3.2 六服务真实闭环

隔离 project `finguard-day4-local` 使用空卷和测试专用凭据启动。验收结果：

- 六服务全部 healthy；MySQL、RabbitMQ、Redis 没有 published host port；
- `/actuator/health` 为 UP，OpenAPI 共 18 paths；
- 匿名业务请求 401，REVIEWER 读取 200、写入 403，ADMIN 对 REVIEWER 决策接口为 403；
- 创建账户 1 条、手工交易 1 条；异步 CSV 导入任务 SUCCESS、2 行入库；
- 自动对账任务 COMPLETED，matched 1、unmatched 1；审核任务由 REVIEWER 确认；
- 审计日志 3 条，统计接口可用，Prometheus 1 个 target 为 UP，Grafana 自动加载 `FinGuard Core Overview`；
- Flyway schema version 为 V10；应用容器为非 root；5 个卷及六服务日志轮转参数符合契约；
- `stop.sh` 后容器保留且 5 个卷存在；重新部署后账户/交易/导入/对账/审核/审计计数仍为 `1/3/1/1/1/3`；
- 验收结束后仅删除 `finguard-day4-local` 的容器、网络、5 个临时卷和测试应用镜像，未操作日常开发栈。

## 4. 首个托管 Linux 发布与部署

GitHub Actions run：[`31472588424`](https://github.com/jkwang1129/finguard-core/actions/runs/31472588424)，结论 success。

| 证据 | 实际值 |
|---|---|
| Git SHA | `e78711a9949160249374bad07136283ecdd5cc63` |
| GHCR tag | `ghcr.io/jkwang1129/finguard-core:e78711a9949160249374bad07136283ecdd5cc63` |
| registry digest | `sha256:de3ef7650f6b2015f9d58df1ffc6b30c29fdf4dc46201c09758214ea9f3dcbdf` |
| test job | success，378/378，6 分 49 秒 |
| publish/deploy job | success，3 分 22 秒 |
| runner | Ubuntu 24.04.4 LTS，Linux 6.17.0-1020-azure x86_64 |
| 资源 | 2 CPU，7.8 GiB 内存，根文件系统 72 GiB/可用 14 GiB |
| 时间 | Etc/UTC，NTP synchronized |
| Docker | Engine 28.0.4，Compose v2.38.2，linux/amd64 |

发布 job 在推送镜像后执行 `preflight.sh` 和 `deploy.sh`，创建 5 个卷并等待六服务健康；application health、OpenAPI、Prometheus health、Grafana health 都返回 HTTP 200，Prometheus target 为 UP。随后执行 `stop.sh`、再次 `deploy.sh` 和相同状态检查，命名卷保持不变。首个发布版本没有已发布的前序 SHA，因此本轮没有伪造回滚结果；升级/回滚由下一次托管运行补证。job 结束时只删除 runner 内 project `finguard-ci-deploy` 的验收资源并退出 GHCR。

### 4.1 跨 SHA 回滚与再升级

修复后的 GitHub Actions run [`31474425853`](https://github.com/jkwang1129/finguard-core/actions/runs/31474425853) 结论为 success：

| 证据 | 实际值 |
|---|---|
| 当前 Git SHA | `8b29433cf76bfcf66633c665188578670ddb924e` |
| 当前 digest | `sha256:9adec42f2f5f85db110ec1c49bf327c0c8001e5ebd254dcd92423ed9cd487a96` |
| 回滚 Git SHA | `ce74fa420ab4e0d64d0a0f286fab9d959228edbf` |
| 回滚 digest | `sha256:d2a37e693e7d19189cce902d97cbd0eba4a67a76c036813b504d520595e135e2` |
| test job | success，378/378，5 分 3 秒 |
| publish/deploy job | success，5 分 26 秒 |

同一组 5 个卷内依次完成：部署当前 SHA、保留卷停止/恢复、回滚到前一 SHA、再次升级到当前 SHA。四次状态门禁均确认 application/OpenAPI/Prometheus/Grafana 为 HTTP 200 且 Prometheus target 为 UP；最终 `.deploy-state/current-image` 为当前 SHA、`previous-image` 为回滚 SHA。失败诊断步骤未触发，`always()` 清理成功移除隔离 runner 的六个容器、网络和 5 个卷。

### 4.2 持久 Alibaba Cloud ECS 验收

主机为用户授权的 Ubuntu 22.04 LTS x86_64 ECS（2 vCPU、约 7.2 GiB 内存、40 GiB 根盘），Docker Engine 29.7.2、Compose v5.4.0，Asia/Shanghai 时钟同步，根盘使用率约 20%。`preflight.sh`、`status.sh` 和 `ss -lntp` 均通过；公网监听仅有 SSH，应用/Prometheus/Grafana 绑定 `127.0.0.1`，MySQL/RabbitMQ/Redis 无宿主机发布端口。主机 UFW 未启用，公网边界由云安全组承担，未开放业务端口。

通过 SSH 本地转发访问 app、Prometheus、Grafana：health、OpenAPI 和三项监控状态均 HTTP 200，Prometheus target 为 UP。匿名业务接口返回 401；ADMIN/REVIEWER 登录成功，REVIEWER 读取账户 200、访问 ADMIN-only 审计接口 403；账户创建 201；CSV 导入 202→SUCCESS；对账 202→COMPLETED；统计和审计查询 200。验收后数据库事实为 1 个账户、1 个导入任务、1 个对账任务、2 条审计记录。

首次启动使用一次性 Bootstrap 创建 ADMIN/REVIEWER，随后关闭 Bootstrap 并重建 app；服务器 `.env.linux` 权限为 600，远端临时 GHCR Token 已删除，仓库未保存任何明文凭据。

持久主机五个命名卷保持不变。`stop.sh` 后五卷仍存在，再启动和状态检查通过。随后完成 `c825e75f1fe61f90ecc9f2feb27325cb50d1e9fb → 8b29433cf76bfcf66633c665188578670ddb924e → c825e75f1fe61f90ecc9f2feb27325cb50d1e9fb`，每次部署健康；最终 current-image 为当前 SHA、previous-image 为旧 SHA，业务/监控数据仍可读。

## 5. 排错记录

### 5.1 运维客户端缺少 curl

本地首次从最小 Docker CLI 容器调用 `status.sh` 时，脚本按预期失败并报告 `required command is unavailable: curl`。按以下链路定位：

1. `docker compose ps` 确认六服务仍为 healthy；
2. 错误发生在脚本前置命令检查，而非应用或依赖健康检查；
3. 在一次性工具容器内安装 `curl`，不修改应用镜像或服务器配置；
4. 重新执行 status，四个 HTTP endpoint 和 Prometheus target 全部通过。

该结果证明缺少运维客户端依赖时会快速失败且不会改变运行栈。真实持久主机接入后，仍需记录至少一次该主机上的排错链路。

### 5.2 Prometheus 首次抓取竞态

第二个 SHA `ce74fa420ab4e0d64d0a0f286fab9d959228edbf` 已通过 378/378、镜像审计并发布，digest 为 `sha256:d2a37e693e7d19189cce902d97cbd0eba4a67a76c036813b504d520595e135e2`，但托管 run [`31473542233`](https://github.com/jkwang1129/finguard-core/actions/runs/31473542233) 的部署生命周期失败，不能作为绿色验收：

1. Compose 已报告六服务 healthy，application/OpenAPI/Prometheus/Grafana endpoint 均为 HTTP 200；
2. 紧接着读取 Prometheus targets 时，首次抓取尚未完成，target 暂时不是 UP；
3. `status.sh` 原实现只查询一次，立即以 `Prometheus does not report an UP target` 退出；
4. 失败诊断输出了六服务有限日志，`always()` 清理仍完整移除隔离容器、网络和 5 个卷；
5. 修复为在 endpoint 健康后最多等待 30 秒、每 2 秒查询一次 target，超时仍失败，避免把固定 sleep 当作成功条件。

修复已由绿色 hosted run `31474425853` 证明；失败 run 本身不计为回滚验收。

## 6. 收尾与边界

持久主机验收已完成。服务器继续保留 FinGuard 六服务和命名卷，便于后续 Day 5/Day 6 使用；Day 5 压测、Day 6 安全测试与故障演练不提前执行。服务器 IP、SSH 私钥、生产口令和 Registry Token 均不进入文档、镜像或 Git。
