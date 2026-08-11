# Week 6 Day 4：Linux 部署验收记录

日期：2026-08-11

## 1. 结论与环境边界

Day 4 的版本化交付物、不可变镜像发布和可重复部署链路已经实现，并在两类隔离 Linux 环境中完成了当前可执行的验证：

- 本机 Docker Desktop Linux Engine：完成六服务业务与监控闭环、停机恢复和数据卷保持验证；
- GitHub-hosted Ubuntu runner：完成真实 CI、私有 GHCR 发布、指定 SHA 拉取、六服务启动、状态检查、停机恢复和资源清理。

当前工作站未发现授权服务器地址、SSH 配置、服务器凭据或可用的常规 Linux WSL 发行版。因此，这两类环境都不能代替“用户授权的持久 Linux 主机 + SSH 隧道 + 主机/云防火墙”验收。本记录严格区分已验证事实和待补证事实，不把 Docker Desktop VM 或临时 CI runner 写成生产服务器。

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

## 5. 排错记录

本地首次从最小 Docker CLI 容器调用 `status.sh` 时，脚本按预期失败并报告 `required command is unavailable: curl`。按以下链路定位：

1. `docker compose ps` 确认六服务仍为 healthy；
2. 错误发生在脚本前置命令检查，而非应用或依赖健康检查；
3. 在一次性工具容器内安装 `curl`，不修改应用镜像或服务器配置；
4. 重新执行 status，四个 HTTP endpoint 和 Prometheus target 全部通过。

该结果证明缺少运维客户端依赖时会快速失败且不会改变运行栈。真实持久主机接入后，仍需记录至少一次该主机上的排错链路。

## 6. 待授权主机补证

以下内容尚无授权目标，不能标记完成：

- 持久 Linux 主机的发行版、资源、磁盘、部署用户、Docker 权限和现有工作负载盘点；
- 主机防火墙与云安全组只允许经批准的 SSH，未授权网络不能直连业务/监控/依赖端口；
- 服务器 `.env.linux` 的 600 权限和最小权限 `read:packages` 拉取凭据；
- 一次性 ADMIN/REVIEWER Bootstrap、关闭并清除明文后仍可登录、数据库只保留 BCrypt；
- 通过 SSH 隧道完成 JWT/RBAC、异步导入、对账、审核、审计、统计和监控验收；
- 持久主机上的停止恢复、两个已发布 SHA 间升级/回滚及业务/监控历史保持。

完成这些项目需要用户提供一台已授权、可 SSH 管理的 Linux 主机入口，以及通过安全会话注入的 GHCR `read:packages` 凭据；若涉及创建云资源、开放安全组或付费，必须另行授权。
