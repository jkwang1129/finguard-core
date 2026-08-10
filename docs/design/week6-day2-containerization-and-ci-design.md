# Week 6 Day 2：应用容器化、完整 Compose 与 GitHub Actions 设计

日期：2026-08-10

## 1. 业务目标

Day 2 不新增业务功能，而是把 Week 1～Week 6 Day 1 已完成的 FinGuard Core 整理为可重复构建、可在干净环境启动、可由持续集成验证的应用交付物。

完成后应形成两条等价入口：

1. 开发者仍可在宿主机使用 Java 17、Maven 和 `127.0.0.1` 依赖运行应用；
2. 开发者也可使用一条 Compose 命令启动应用、MySQL、RabbitMQ 和 Redis，由应用通过 Compose 服务 DNS 连接依赖。

GitHub Actions 负责在干净 runner 上运行完整测试、打包可执行 JAR 并构建应用镜像。Day 2 只做 CI，不做镜像发布和服务器部署。

## 2. 当前事实基线

- Week 6 Day 1 提交为 `6ca94b8`，历史完整测试为 370/370；Day 2 必须重新执行。
- 项目使用 Java 17、Maven 3.9+、Spring Boot 3.5.16 和 Spring Boot 可执行 JAR。
- `docker-compose.yml` 已有 MySQL 8.4.10、RabbitMQ 4.3.4-management 和 Redis 8.2.8-alpine，三者均有健康检查和命名卷。
- 当前数据源 URL 固定使用 `127.0.0.1`；RabbitMQ 和 Redis 已支持 host 环境变量。
- 当前 Flyway 最新版本为 V10，本日不修改迁移。
- 当前仓库没有 `Dockerfile`、`.dockerignore`、Compose 应用服务和 GitHub Actions workflow。
- 当前仓库没有 Git remote；因此本地可验证 workflow 内容和等价命令，但 GitHub-hosted run 必须等远端存在后真实执行，不能虚报。

## 3. 版本与镜像选择

### 3.1 构建镜像

```text
maven:3.9.16-eclipse-temurin-17-alpine
```

选择原因：

- Maven 版本与当前本机 3.9.16 一致；
- Java 主版本与项目 Java 17 契约一致；
- 使用 Docker Official Image 的明确版本标签，不使用 `latest`；
- 构建阶段可以缓存 Maven 依赖，运行镜像不携带 Maven。

### 3.2 运行镜像

```text
eclipse-temurin:17-jre-ubi9-minimal
```

选择原因：

- 只包含 Java 17 JRE，不携带 JDK 和 Maven；
- UBI minimal 变体具有稳定的 Linux 用户工具和 `wget`，可创建非 root 用户并完成 HTTP 健康检查；
- 使用 Java 17 版本系列而不是漂移的 `latest`。

### 3.3 GitHub Actions

```text
actions/checkout@v7
actions/setup-java@v5
```

选择原因：

- 使用 2026-08-10 官方发布页中的当前主版本；
- `setup-java` 负责 Temurin 17 与 Maven 依赖缓存；
- GitHub-hosted runner 已提供 Docker，因此 Day 2 直接使用 `docker build`，不增加镜像推送相关 action。

版本依据：

- https://github.com/actions/checkout/releases
- https://github.com/actions/setup-java/releases
- https://hub.docker.com/_/maven
- https://hub.docker.com/_/eclipse-temurin

## 4. Docker 构建设计

```text
构建阶段
  → 复制 pom.xml
  → dependency:go-offline 缓存依赖层
  → 复制 src
  → 跳过测试打包可执行 JAR

运行阶段
  → 创建固定 UID/GID 的 finguard 非 root 用户
  → 只复制可执行 JAR
  → 暴露 8080
  → wget 检查 /actuator/health
  → exec 形式启动 java -jar
```

Docker 构建中的打包使用 `-DskipTests`，因为镜像构建环境不应偷偷依赖 MySQL/RabbitMQ/Redis。完整测试必须在本地验收和 CI 的 `mvn clean package` 阶段先执行，不能把跳过测试当作 CI 结果。

`.dockerignore` 排除：

- `.env` 和所有本机日志；
- `.git`、IDE 配置和操作系统临时文件；
- 宿主机 `target`；
- 与运行构建无关的文档和本地脚本。

最终镜像不得包含源码、Maven、本机 `target`、Git 历史或真实凭据。

## 5. 配置注入设计

### 5.1 宿主机模式

```text
MYSQL_HOST=127.0.0.1（默认）
RABBITMQ_HOST=127.0.0.1（默认）
REDIS_HOST=127.0.0.1（默认）
```

原有 `mvn spring-boot:run` 方式保持可用。

### 5.2 Compose 模式

```text
MYSQL_HOST=mysql
RABBITMQ_HOST=rabbitmq
REDIS_HOST=redis
```

容器内的 `127.0.0.1` 只指向应用容器自身，因此必须使用 Compose 服务名。端口使用容器内部固定端口 3306、5672 和 6379，不使用宿主机映射端口。

### 5.3 敏感信息

以下值只在运行时由环境变量注入：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `RABBITMQ_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET_BASE64`

`.env.example` 只能提供明显不可用于真实环境的占位值；`.env` 继续被 Git 忽略。Compose 文件、Dockerfile、镜像层、workflow 和日志不得保存真实值。

## 6. Compose 设计

新增 `app` 服务：

- 由仓库根目录 `Dockerfile` 构建，镜像名默认为 `finguard-core:local`；
- 映射 `127.0.0.1:${FINGUARD_APP_PORT:-8080}:8080`；
- 通过 `depends_on.condition: service_healthy` 等待三个依赖；
- 通过环境变量使用 `mysql`、`rabbitmq` 和 `redis` 服务名；
- 要求运行时提供四类依赖密码和 JWT 密钥；
- 使用镜像内 `wget` 检查 `/actuator/health`；
- 使用 30 秒停止宽限期，让 Spring Boot、连接池和消息消费者有机会正常关闭；
- 使用可配置的 768 MiB 默认内存限制，Java 依据容器内存计算堆大小；
- 不挂载源码或本机 Maven 仓库，不把开发目录变成运行时依赖。

MySQL、RabbitMQ 和 Redis 的命名卷保持不变。重建应用镜像或应用容器不应删除数据卷。

## 7. GitHub Actions 设计

触发条件：

```text
push
pull_request
workflow_dispatch
```

权限只保留 `contents: read`。流水线顺序：

1. checkout 源码；
2. setup Temurin 17 和 Maven 缓存；
3. 使用仅供 CI 的非生产占位环境变量启动 MySQL/RabbitMQ/Redis；
4. `docker compose up -d --wait mysql rabbitmq redis` 等待健康；
5. `mvn -B -ntp clean package` 一次完成编译、完整测试和 JAR 打包；
6. `docker build` 构建以提交 SHA 标记的应用镜像；
7. 检查镜像配置中的非 root 用户和 Java 17；
8. 失败时仅输出 Compose 状态和有限依赖日志；
9. `always()` 清理 runner 上的容器，不执行镜像推送。

CI 使用测试专用固定 JWT 值和明显的 CI 密码。它们不是生产凭据，不从仓库读取 `.env`，也不使用 GitHub production secrets。

## 8. 测试与验收矩阵

| 层级 | 验证 | 通过标准 |
|---|---|---|
| Compose 静态 | `docker compose config --quiet` | 配置可解析、必需变量齐全 |
| Java | `mvn clean package` | 全部测试通过、0 skipped、生成可执行 JAR |
| 镜像构建 | `docker build` | 多阶段构建成功 |
| 镜像内容 | `docker image inspect` 与容器内检查 | 非 root、Java 17、无 Maven/源码/`.env` |
| 空库启动 | 独立 Compose project | MySQL/RabbitMQ/Redis/app 全部 healthy，Flyway V1→V10 |
| HTTP | health/OpenAPI/login/受保护接口 | 200；匿名业务请求仍 401，越权仍 403 |
| 重启 | 重启 app 容器 | 依赖数据保留，应用重新 healthy |
| GitHub Actions | push/PR/workflow_dispatch | 测试、打包、镜像构建 job 全绿 |
| 清理 | 项目范围检查 | 只移除 Day 2 临时资源，不删除原有开发卷 |

## 9. 失败路径

| 失败 | 预期诊断 |
|---|---|
| 数据库仍写死 localhost | app 日志出现数据库连接拒绝；检查 `MYSQL_HOST=mysql` |
| 依赖未健康 | app 不应提前启动；检查 Compose health 与 `depends_on` |
| JWT 缺失/过短 | app 启动失败并明确提示配置错误，不使用仓库默认密钥 |
| 镜像构建误带 `.env` | 镜像内容审计失败，修正 `.dockerignore` 后重建 |
| CI 测试连接失败 | 输出 Compose 状态和有限依赖日志，不输出环境变量 |
| GitHub remote 缺失 | 本地项目保持可用，但 Actions 真实运行项不得标记完成 |

## 10. 回滚

- 删除 `Dockerfile`、`.dockerignore`、workflow 和本设计文档；
- 从 Compose 中移除 `app` 服务；
- 恢复 `application.yml`、`.env.example`、README 和 `TASKS.md` 的 Day 2 改动；
- 只删除明确使用独立 Day 2 project name 创建的临时容器、网络、临时卷和镜像；
- 不执行针对现有开发环境的 `docker compose down -v`；
- 不修改或删除 Flyway V1～V10，不回退 OpenAPI、JWT/RBAC 或任何既有业务能力。

## 11. Day 3 交接边界

Day 2 结束时只提供应用与依赖的健康运行基础。Day 3 才增加 Micrometer 自定义业务指标、Prometheus 抓取和 Grafana 仪表盘；Day 2 不提前公开 `/actuator/prometheus`，也不添加监控容器。
