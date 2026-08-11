# Week 6 Day 4：不可变镜像发布与真实 Linux 部署设计

日期：2026-08-11

## 1. 业务目标

Day 4 不增加业务功能，而是把 Day 3 已通过本地验证的六服务栈交付到一台用户授权的真实 Linux 主机。交付物必须同时满足：

1. 发布的应用镜像与一个完整 Git SHA 一一对应；
2. 服务器不编译 Java 源码，只拉取已经通过 CI 的镜像；
3. MySQL、RabbitMQ 和 Redis 不发布宿主机端口；
4. 应用、Prometheus 和 Grafana 只监听服务器回环地址，通过 SSH 隧道访问；
5. 启动、状态检查、停止、升级和回滚可以重复执行；
6. 日常停止和回滚不删除命名卷；
7. 所有真实凭据都留在服务器私有环境或临时登录会话中。

Day 5 才做 JMeter 压测和性能优化；Day 6 才做安全报告与三次故障演练。

## 2. 当前事实基线

- Day 3 本地提交为 `6f0c284`，历史完整回归为 378/378；进入 Day 4 时本地 `main` 比 `origin/main` 领先 1 个提交。
- 当前远端为私有仓库 `jkwang1129/finguard-core`，已有真实绿色 GitHub-hosted CI，但 workflow 只构建镜像，不发布镜像。
- 当前 Docker Compose 已有 app、MySQL、RabbitMQ、Redis、Prometheus 和 Grafana，以及健康检查和五个命名卷。
- 开发 Compose 中 MySQL 端口可能发布到所有宿主机网卡，不能直接用于服务器。
- Flyway 最新版本是 V10；Day 4 不增加迁移。
- 当前工作站没有 SSH 配置、服务器环境变量或额外 WSL Linux 发行版。真实主机的发行版、架构、资源、网络和防火墙必须在连接后实测，不能预填。

## 3. 依据与设计选择

- GitHub Container Registry 允许工作流使用仓库的 `GITHUB_TOKEN` 发布关联包，工作流 job 使用 `contents: read` 与 `packages: write`；服务器只需要 classic PAT 的 `read:packages` 权限。参考 [GitHub Container Registry 文档](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)。
- 镜像加入 `org.opencontainers.image.source` 标签，使 GHCR 包在首次发布时关联当前仓库并继承访问边界。参考 [GitHub Packages 权限文档](https://docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility)。
- Docker 官方建议单机生产部署使用额外的 Compose 配置，覆盖端口、环境和 restart 等生产差异。Day 4 使用独立 `compose.linux.yml`，避免列表合并导致开发端口残留。参考 [Docker Compose 生产部署文档](https://docs.docker.com/compose/how-tos/production/)。
- Docker Engine 和 Compose Plugin 必须使用目标发行版对应的 Docker 官方仓库安装方式；进入真实主机后才选择 Ubuntu、Debian 或 RPM 系列步骤，不把某个发行版命令写成通用事实。参考 [Docker Engine 安装文档](https://docs.docker.com/engine/install/) 与 [Compose Plugin 安装文档](https://docs.docker.com/compose/install/linux/)。

## 4. 发布契约

发布只发生在 `main` 分支的 `push` 事件：

```text
test-package-image
  → 启动真实 MySQL/RabbitMQ/Redis
  → 校验开发与 Linux Compose、Shell 语法、Prometheus/Grafana
  → mvn clean package
  → 构建并检查本地应用镜像

publish-image（needs: test-package-image）
  → 重新检出同一个 github.sha
  → 构建 ghcr.io/jkwang1129/finguard-core:<40 位 SHA>
  → 检查非 root、Java 17、源码/Maven缺失、source/revision 标签
  → 使用 GITHUB_TOKEN 登录 GHCR
  → 推送并记录 registry digest
```

Pull Request、非 `main` 分支和测试失败均不得发布。发布 job 单独申请 `packages: write`，测试 job 保持 `contents: read`。不创建 `latest`、`main` 等可漂移部署标签。

发布镜像同时有两类身份：

- tag：选择一个具体 Git 提交；
- digest：证明 registry 中实际内容。

部署环境文件记录完整 SHA tag，验收记录同时记录 digest。回滚不得只写“旧版本”，必须给出旧完整 SHA。

## 5. Linux Compose 契约

`compose.linux.yml` 是服务器独立运行文件，不与开发 Compose 合并：

| 服务 | 宿主机端口 | 数据 | 关键约束 |
|---|---|---|---|
| app | `127.0.0.1:8080` | 无业务卷 | 完整 SHA 镜像、非 root、健康检查、768 MiB 默认限制 |
| prometheus | `127.0.0.1:9090` | `prometheus_data` | 只抓取 Compose 内网 app |
| grafana | `127.0.0.1:3000` | `grafana_data` | 禁止匿名和自助注册 |
| mysql | 不发布 | `mysql_data` | V1→V10、强随机两类密码 |
| rabbitmq | 不发布 | `rabbitmq_data_v4` | 业务与管理端口均只在 Compose 网络 |
| redis | 不发布 | `redis_data_v1` | requirepass、AOF |

全部服务使用 `restart: unless-stopped`、健康检查、停止宽限期和 `json-file` 日志轮转（10 MiB × 3）。日常停止使用 `docker compose stop`，不会删除容器、网络或卷。

## 6. 目录、权限与配置

建议服务器目录为部署用户拥有的固定目录，例如：

```text
/opt/finguard-core/
├── compose.linux.yml
├── .env.linux                 # 600，不进入 Git
├── ops/
├── scripts/linux/
└── .deploy-state/             # 700，只记录当前/上一个镜像名
```

实际目录可以调整，但同一次部署必须固定。`.env.linux` 从 `.env.linux.example` 创建，至少包含：

- 完整 SHA 应用镜像；
- MySQL root/app 强随机密码；
- RabbitMQ、Redis、Grafana 强随机密码；
- 随机 32 字节以上 Base64 JWT 密钥；
- 首次 Bootstrap 的 ADMIN/REVIEWER 用户名和强随机密码。

环境文件只允许部署用户读写。`.deploy-state` 不保存凭据，只保存成功部署的 current/previous image，用于无参数回滚。

## 7. 初始账号

项目没有用户管理 API，因此首批 ADMIN/REVIEWER 使用已有 Bootstrap：

1. 首次启动前设置 `FINGUARD_AUTH_BOOTSTRAP_ENABLED=true` 和两组强随机凭据；
2. 应用启动成功后验证两组 JWT 登录；
3. 确认数据库只保存 BCrypt 哈希；
4. 把 Bootstrap 开关改回 `false`，删除环境文件中的四项明文 Bootstrap 用户名/密码；
5. `deploy.sh` 重建 app 并再次验证登录。

不能只关闭开关却继续把明文密码留在服务器环境文件。

## 8. 脚本职责

| 脚本 | 职责 | 明确不做 |
|---|---|---|
| `preflight.sh` | 系统/资源/时间/Docker/端口/防火墙提示、环境权限和 Compose 检查 | 安装软件、修改防火墙、打印秘密 |
| `deploy.sh` | 校验完整 SHA、临时 GHCR 登录、pull、`up --wait`、记录成功镜像 | build、删除卷、修改业务数据 |
| `status.sh` | Compose 状态、health/OpenAPI、Prometheus/Grafana、target UP | 输出环境变量、修改容器 |
| `stop.sh` | 30 秒优雅停止且保留全部资源 | `down`、`down -v`、卷删除 |
| `rollback.sh` | 原子修改环境文件中的应用镜像并调用 deploy | 数据库迁移回滚、卷恢复 |

如调用者提供 `GHCR_TOKEN`，`deploy.sh` 使用临时 `DOCKER_CONFIG` 登录，并在退出时删除临时目录，避免把 Token 永久留在默认 Docker 配置。Token 只需要 `read:packages`。

## 9. 安全访问

主机/云防火墙默认只批准 SSH。客户端使用本地端口转发：

```bash
ssh \
  -L 8080:127.0.0.1:8080 \
  -L 9090:127.0.0.1:9090 \
  -L 3000:127.0.0.1:3000 \
  <authorized-host>
```

然后只在客户端访问 `127.0.0.1:8080/9090/3000`。真实验收必须同时核对：

- 云安全组/主机防火墙；
- `ss -lnt`；
- `docker compose ps` 的 published ports；
- 从未授权网络无法直接访问 8080/9090/3000/3306/5672/6379/15672。

Day 4 不增加 Nginx、域名或 TLS。若未来公开 API，必须以独立 HTTPS 里程碑处理。

## 10. 部署、升级与回滚

### 10.1 首次部署

```bash
chmod 600 .env.linux
sh scripts/linux/preflight.sh
GHCR_USERNAME=<username> GHCR_TOKEN=<read-packages-token> \
  sh scripts/linux/deploy.sh
sh scripts/linux/status.sh
```

命令行只写变量名，不把 Token 字面值粘贴进 shell history；实际执行建议从安全会话变量或秘密管理器注入。

### 10.2 安全停止和恢复

```bash
sh scripts/linux/stop.sh
sh scripts/linux/deploy.sh
sh scripts/linux/status.sh
```

停止前后记录五个卷、Flyway V10、业务行计数和 Prometheus/Grafana 可用性。

### 10.3 A→B 升级

把 `.env.linux` 中 `FINGUARD_APP_IMAGE` 改为另一个已通过 CI 且兼容 V10 的完整 SHA，再执行 `deploy.sh`。脚本在成功后把原镜像写入 `previous-image`。

### 10.4 B→A 回滚

```bash
sh scripts/linux/rollback.sh
sh scripts/linux/status.sh
```

回滚只替换 app 镜像并保留数据库/消息/缓存/监控卷。Day 4 没有新迁移，因此 A、B 都必须兼容 V10。未来不可逆迁移必须先设计备份和前向修复，不能把镜像回滚宣传为数据库回滚。

## 11. 真实验收矩阵

| 层级 | 验证 | 通过标准 |
|---|---|---|
| 主机 | `preflight.sh` | Linux、资源、时间、Docker、Compose、端口和权限均有真实记录 |
| CI | GitHub-hosted run | 完整测试先通过，publish job 后执行 |
| Registry | tag/digest/labels | 完整 SHA、digest、source/revision、private access 正确 |
| Compose | config/ps/inspect | 六服务 healthy，无 build，依赖服务无宿主机端口，日志轮转生效 |
| 数据库 | Flyway/计数 | V1→V10，初始账号为 BCrypt，重启/回滚后业务事实不丢失 |
| API | health/OpenAPI/JWT/RBAC | 200、匿名 401、越权 403、ADMIN/REVIEWER 权限不回退 |
| 业务 | 导入/对账/审核/审计/统计 | 异步闭环完成，临时数据可定位并清理 |
| 监控 | target/Grafana | target UP、9 个面板自动装载、应用重建后历史仍在 |
| 生命周期 | stop/start/upgrade/rollback | 每步健康，不删除卷，不丢数据/消息/监控历史 |
| 安全 | firewall/ss/ports/secrets | 只批准 SSH 公网入口，环境 600，仓库/镜像/日志无秘密 |

## 12. 失败路径与排错顺序

固定排错链路：

```text
preflight
  → docker compose config --quiet
  → docker compose ps
  → docker compose logs --no-color --tail=100 <service>
  → 检查镜像 SHA/digest、健康检查、端口和服务 DNS
  → 修复配置或权限
  → deploy/status 与最小业务 smoke
```

常见失败：

| 失败 | 诊断与处理 |
|---|---|
| GHCR 401/403 | 核对用户名、classic PAT `read:packages` 和 package/repository access；不改成公开包绕过 |
| 镜像不是完整 SHA | preflight/deploy 必须直接拒绝；修正环境文件 |
| app 等待数据库 | 先看 MySQL health 与密码是否和现有卷初始化值一致；不得删卷“修复” |
| 端口被占用 | 用 `ss -lnt` 定位；不得停止不属于 FinGuard 的服务 |
| target DOWN | 核对 app health、Compose DNS 和 Prometheus 配置，不公开 Actuator 其他端点 |
| 环境文件权限过宽 | `chmod 600 .env.linux`，再执行脚本 |
| 回滚后仍是新版本 | 核对 `.env.linux`、current/previous state、容器实际 image ID 和 registry digest |

## 13. 回滚与清理边界

- 普通停止只执行 `stop.sh`。
- 应用回滚只切换完整 SHA，不删除任何命名卷。
- 验收临时业务数据按外键顺序清理，并独立复核残留计数。
- 本机独立 Compose 验收只能清理专用 project/volume；不得对开发栈执行 `docker compose down -v`。
- 彻底下线、卷删除、云主机销毁、仓库/包可见性变更均需要新的明确授权。
- 不修改 Flyway V1～V10，不引入 Day 5/Day 6 能力。
