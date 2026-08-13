# FinGuard Core 部署与回滚

## 1. 部署模型

生产化练习采用单台 Linux + Docker Compose 六服务：应用、MySQL、RabbitMQ、Redis、Prometheus、Grafana。应用镜像由 GitHub Actions 构建并以完整 40 位 Git SHA 标记；部署和回滚都不使用 `latest`。

这是单机部署练习，不包含 TLS 终止、负载均衡、Kubernetes、多可用区数据库或自动灾备。公网主机只应开放 SSH；应用与监控经回环地址和 SSH 隧道访问，三项数据依赖不发布宿主机端口。

## 2. 本地六服务

复制示例环境文件并填写随机、仅本机使用的密码。JWT 密钥必须在运行时生成，不得提交：

```powershell
Copy-Item .env.example .env
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtBytes) } finally { $rng.Dispose() }
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
```

安全停止保留数据卷：

```powershell
docker compose down
Remove-Item Env:JWT_SECRET_BASE64
```

日常停止禁止加 `-v`。如需一次性全链路验收，优先使用固定隔离项目的 `scripts/acceptance/invoke-week6-day7-acceptance.ps1`，它会生成临时秘密并在 `finally` 清理自己的资源。

## 3. Linux 首次部署

将 `compose.linux.yml`、`.env.linux.example`、`ops/` 和 `scripts/linux/` 放入固定部署目录：

```bash
cp .env.linux.example .env.linux
chmod 600 .env.linux
sh scripts/linux/preflight.sh
```

`.env.linux` 至少需要：

- `FINGUARD_APP_IMAGE=ghcr.io/<owner>/finguard-core:<full-sha>`；
- MySQL root/app、RabbitMQ、Redis、Grafana 的独立随机密码；
- Base64 编码、解码后至少 32 字节的随机 JWT 密钥；
- 首次创建 ADMIN/REVIEWER 时的一次性 Bootstrap 凭据。

私有 GHCR 只授予拉取权限：

```bash
export GHCR_USERNAME='<github-user>'
export GHCR_TOKEN='<read-packages-token>'
sh scripts/linux/deploy.sh
unset GHCR_TOKEN
sh scripts/linux/status.sh
```

部署脚本使用临时 Docker 登录配置并在退出时删除。六服务必须全部 healthy，Flyway 到预期版本，Prometheus target 必须为 `UP`。

## 4. 首次用户与访问

Bootstrap 只用于首次建用户。验证 ADMIN/REVIEWER 都能登录后，应把 `FINGUARD_AUTH_BOOTSTRAP_ENABLED=false`，删除环境文件中的四项 Bootstrap 用户名/密码，再次执行部署和状态检查。数据库只保留 BCrypt 哈希。

SSH 隧道示例：

```bash
ssh -L 8080:127.0.0.1:8080 \
    -L 9090:127.0.0.1:9090 \
    -L 3000:127.0.0.1:3000 \
    <authorized-host>
```

## 5. 升级与回滚

升级：把 `.env.linux` 的镜像改为另一个已通过 CI 的完整 SHA，然后执行 `deploy.sh` 和 `status.sh`。回滚：

```bash
sh scripts/linux/rollback.sh
sh scripts/linux/status.sh
```

也可以把目标旧镜像作为 `rollback.sh` 参数。回滚只替换应用镜像并保留五个数据卷；它不会回滚 Flyway、数据库事实或外部副作用。因此包含不兼容迁移的发布必须先设计前后兼容和数据恢复方案。

## 6. 发布检查单

1. 工作树和目标提交明确，完整 Java 17 测试通过。
2. GitHub Actions 测试、镜像检查和 SHA 镜像发布成功。
3. `.env.linux` 权限为 600（部署读取阶段可收紧为 400），日志不显示值。
4. 六服务 healthy；MySQL/RabbitMQ/Redis 无宿主机发布端口。
5. health、OpenAPI、JWT 401/403、业务 smoke、Prometheus target、Grafana 健康通过。
6. 记录当前/上一镜像 SHA；回滚不删除卷。

Day 4 的真实 Linux 验收见[部署验收记录](review/week6-day4-linux-deployment-acceptance.md)。
