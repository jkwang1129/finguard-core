# Linux 部署、升级与回滚 Runbook

## 目的与前提

本手册管理单台 Linux 上的 FinGuard 六服务 Compose 部署。应用、Prometheus 和 Grafana 只绑定回环；MySQL、RabbitMQ、Redis 不发布宿主机端口。应用镜像必须来自已通过 CI 的完整 40 位 Git SHA 标签，禁止使用 `latest`。

部署目录必须包含 `compose.linux.yml`、`config/`、`scripts/linux/` 和权限为 600 的 `.env.linux`。环境文件存放变量值，但本手册不展示任何真实秘密。

## 1. 主机与配置预检

```sh
sh scripts/linux/preflight.sh
```

确认主机/时间/磁盘、Docker/Compose、现有容器、监听端口、防火墙可见性和 Compose 配置。输出可以显示目标镜像，但不得渲染秘密值。预检失败时不部署。

## 2. 首次部署或升级

在 `.env.linux` 中将 `FINGUARD_APP_IMAGE` 设置为已通过 CI 的完整 SHA 镜像，然后执行：

```sh
sh scripts/linux/deploy.sh
sh scripts/linux/status.sh
```

`deploy.sh` 校验镜像、拉取固定依赖、以 `--no-build` 启动并等待健康，再记录 current/previous image。`status.sh` 必须确认 Compose 状态、应用 health、OpenAPI、Prometheus、Grafana 及 Prometheus target `UP`。

如私有 GHCR 拉取需要临时登录，只通过当前进程环境提供 `GHCR_USERNAME` 和 `GHCR_TOKEN`；脚本使用临时 Docker 配置并在退出时删除。不得把 Token 写入 `.env.linux`、日志或命令行参数。

## 3. 查看有限日志

```sh
sh scripts/linux/logs.sh app 100
sh scripts/linux/logs.sh mysql 100
sh scripts/linux/logs.sh rabbitmq 100
```

服务只允许 `app|mysql|rabbitmq|redis|prometheus|grafana`，行数为 1～999。命令不跟随日志流；先从 100 行开始，只在证据不足时增加。

## 4. 安全停止与恢复

```sh
sh scripts/linux/stop.sh
sh scripts/linux/deploy.sh
sh scripts/linux/status.sh
```

`stop.sh` 只停止容器，保留容器、网络和 5 个命名卷。恢复后必须重新验证登录、数据库事实、队列/缓存状态以及 Prometheus/Grafana，不以进程启动代替业务验收。

## 5. 应用镜像回滚

无参数回滚到 `.deploy-state/previous-image`：

```sh
sh scripts/linux/rollback.sh
sh scripts/linux/status.sh
```

也可以显式给出一个已验收的完整 SHA 镜像：

```sh
sh scripts/linux/rollback.sh ghcr.io/jkwang1129/finguard-core:<FULL_40_CHARACTER_GIT_SHA>
sh scripts/linux/status.sh
```

回滚脚本以权限受控的临时文件更新 `.env.linux`，然后复用部署流程。它只切换应用镜像并保留命名卷，不回滚 Flyway、业务数据、已发送消息或其他外部副作用。目标版本与当前数据库迁移不兼容时禁止回滚，必须使用事先设计的数据恢复或前向修复方案。

## 6. 发布后检查单

1. `status.sh` 全部通过，Prometheus target 为 `UP`。
2. 实际容器镜像与目标完整 SHA/digest 一致。
3. 依赖端口未发布，应用和监控仍为回环监听。
4. `.env.linux` 权限保持 600，Bootstrap 已关闭。
5. 最小 JWT/RBAC、业务读取和监控 smoke 通过。
6. current/previous image 可追溯；没有删除数据卷。
7. 临时 GHCR 登录配置和本地 Token 已清理。

详细拓扑和已验证生命周期见 [部署文档](../DEPLOYMENT.md) 与 [Day 4 Linux 验收](../review/week6-day4-linux-deployment-acceptance.md)。
