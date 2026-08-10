# Week 6 Day 1：生产化契约与 OpenAPI 演示基线

日期：2026-08-10

## 1. 业务目标

Week 6 不再扩展核心业务，而是把已经完成的 FinGuard Core 整理为可部署、可观测、可验证、可演示的实习项目。Day 1 先补齐 P0 中尚未完成的 Swagger/OpenAPI，并锁定 Day 2～Day 7 的生产化边界。

本日完成后，第三方应当能够：

1. 匿名打开 Swagger UI 并阅读接口；
2. 通过登录接口获取 JWT；
3. 在 Swagger UI 中配置 Bearer Token；
4. 按 ADMIN / REVIEWER 权限调用现有业务接口；
5. 看到未认证 `401`、权限不足 `403` 等真实安全行为。

Swagger 只描述和调用既有接口，不改变 Service、事务、幂等、数据库约束和 RabbitMQ 可靠性语义，也不能替代自动化测试。

## 2. 当前事实基线

- Week 5 已在提交 `8e35731` 收口，复盘记录的完整回归基线为 366/366；该数字必须由 Day 1 重新执行验证，不能直接复制为本日结论。
- 当前应用使用 Spring Boot 3.5.16、Java 17、Spring MVC、Spring Security Resource Server 和 JWT。
- 当前匿名端点只有 `POST /api/auth/login` 与 `GET /actuator/health`。
- 当前没有 Springdoc 依赖、OpenAPI 配置、Swagger UI 或文档端点安全白名单。
- Flyway 最新版本仍是 V10，本日不修改数据库结构。
- Docker 镜像、GitHub Actions、Micrometer 业务指标、Prometheus/Grafana、Linux 部署、JMeter、安全报告和故障演练均未实现，属于 Day 2 之后。

## 3. 依赖选择

项目采用：

```text
org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17
```

选择理由：

- 当前项目是 Spring MVC，不是 WebFlux，因此使用 `webmvc-ui` starter；
- Springdoc 官方兼容矩阵将 Spring Boot 3.5.x 对应到 Springdoc 2.8.x；
- 2026-08-10 核对时，Springdoc 官方将 2.8.17 标为 Spring Boot 3 的稳定版本；
- 不使用面向 Spring Boot 4 的 Springdoc 3.x，也不添加旧的 Springdoc 1.x 依赖。

参考：

- https://springdoc.org/faq.html
- https://springdoc.org/

## 4. 目标请求流

```text
匿名访问 /swagger-ui/index.html 或 /v3/api-docs
  → Spring Security 文档白名单放行
  → Springdoc 从 Controller、DTO 和校验注解生成 OpenAPI
  → 调用 POST /api/auth/login 获取 JWT
  → Swagger UI Authorize 填入 JWT
  → Authorization: Bearer <token>
  → 既有 Spring Security 继续执行 ADMIN / REVIEWER RBAC
  → Controller → Service → MySQL / Redis / RabbitMQ
```

OpenAPI 中的 Bearer Scheme 只负责告诉客户端如何传 Token；真正的认证和授权仍由 `SecurityFilterChain`、`JwtDecoder` 和数据库角色完成。

## 5. API 分组与权限契约

| 分组 | 主要路径 | 权限 |
|---|---|---|
| Authentication | `POST /api/auth/login` | 匿名 |
| Accounts | `/api/accounts/**` | ADMIN 写；ADMIN/REVIEWER 读 |
| Transactions | `/api/transactions/**` | ADMIN 写；ADMIN/REVIEWER 读 |
| Import Jobs | `/api/import-jobs/**` | ADMIN 上传；ADMIN/REVIEWER 查询 |
| Reconciliation | `/api/reconciliation-jobs/**` | ADMIN 创建；ADMIN/REVIEWER 查询 |
| Review Tasks | `/api/review-tasks/**` | ADMIN/REVIEWER 查询；REVIEWER 决策 |
| Audit Logs | `GET /api/audit-logs` | ADMIN |
| Statistics | `GET /api/statistics/overview` | ADMIN/REVIEWER |

文档端点：

```text
GET /swagger-ui.html
GET /swagger-ui/**
GET /v3/api-docs
GET /v3/api-docs/**
```

以上文档端点允许匿名访问；所有 `/api/**` 业务权限保持现状。

## 6. OpenAPI 设计决定

1. 使用统一 `OpenApiConfiguration` 声明项目标题、版本、简介、JWT Bearer Scheme 和全局安全要求。
2. 登录操作使用 `@SecurityRequirements` 显式覆盖全局 Bearer 要求，避免 Swagger UI 错误地要求“先登录才能登录”。
3. Controller 使用稳定英文 Tag 和简洁中文/英文 Summary 描述业务用途，不把 Service 实现、SQL、异常堆栈或内部配置暴露到文档。
4. DTO、枚举、分页和 Jakarta Validation 由 Springdoc 自动生成 Schema；本日不为了“文档好看”复制一套文档 DTO。
5. 隐藏框架参数 `HttpServletRequest`、`Jwt` 等内部注入对象，避免将它们误生成为客户端参数。
6. OpenAPI JSON 不提供真实用户名、密码、JWT、数据库连接、RabbitMQ/Redis 凭据或 `.env` 内容。
7. 不额外公开 Actuator 其他端点；Prometheus 暴露策略留给 Day 3。

## 7. 测试矩阵

### 7.1 OpenAPI 文档测试

- 匿名 `GET /v3/api-docs` 返回 `200` 和 JSON；
- `openapi` 版本存在；
- `info.title`、`info.version` 符合配置；
- `components.securitySchemes.bearerAuth` 为 HTTP Bearer/JWT；
- 登录、账户、交易、导入、对账、审核、审计、统计路径全部存在；
- 登录操作没有安全要求；受保护业务操作存在 `bearerAuth`；
- 文档不包含测试密钥或本地 `.env` 值。

### 7.2 Swagger UI 与 RBAC 回归

- 匿名 `GET /swagger-ui/index.html` 返回 `200`；
- 匿名业务请求仍返回 `401 AUTHENTICATION_REQUIRED`；
- 无权限角色仍返回 `403 ACCESS_DENIED`；
- 既有 ADMIN/REVIEWER 权限矩阵测试继续通过。

### 7.3 完整回归与真实验收

- 运行新增聚焦测试；
- 运行完整 `mvn clean test`，记录实际 total/failures/errors/skipped；
- 启动真实应用并检查 `/actuator/health`、`/v3/api-docs` 和 Swagger UI；
- 使用真实 ADMIN/REVIEWER 登录 JWT 验证文档、读写和越权路径；
- 清理临时用户、业务数据、Redis key、RabbitMQ 消息、应用进程和端口。

## 8. 文件计划

```text
pom.xml
TASKS.md
src/main/resources/application.yml
src/main/java/com/finguard/core/config/OpenApiConfiguration.java
src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java
src/main/java/com/finguard/core/**/controller/*Controller.java
src/test/java/com/finguard/core/OpenApiHttpIntegrationTest.java
docs/design/week6-day1-productionization-and-openapi-contract.md
```

## 9. Day 2～Day 7 边界

| Day | 锁定交付 |
|---|---|
| Day 2 | Dockerfile、完整 Compose 应用服务和 GitHub Actions 镜像构建 |
| Day 3 | Micrometer 自定义指标、Prometheus 和 Grafana |
| Day 4 | 真实 Linux 部署、启动/停止/回滚和排错记录 |
| Day 5 | JMeter 压测、EXPLAIN/指标证据和一次可解释优化 |
| Day 6 | 安全测试清单与至少三次可复现故障演练 |
| Day 7 | 全量验收、最终文档、演示材料、面试笔记和可验证简历描述 |

实际进入后续 Day 时仍需按当前仓库事实细化；本表只锁定职责，不能把后续计划描述为已实现。

## 10. 本日范围边界

Day 1 不实现：

- Dockerfile、应用容器或 Compose 应用服务；
- GitHub Actions；
- Micrometer 自定义业务指标；
- Prometheus、Grafana 或新的 Actuator 暴露端点；
- Linux 部署、JMeter、安全报告或故障演练；
- 新数据库表、Flyway V11、业务接口或业务状态；
- 前端页面、API Gateway、代码生成器、Kubernetes、OpenTelemetry 或 ELK。

## 11. 回滚

若 Springdoc 引起启动或安全兼容问题，可移除 Springdoc 依赖、OpenAPI 配置、Controller 文档注解、文档白名单和对应测试。回滚不得修改 V1～V10、业务数据、JWT/RBAC 语义或 Week 1～Week 5 功能。

## 12. 完成标准

- OpenAPI JSON 与 Swagger UI 可匿名访问；
- JWT Bearer Scheme、全部核心路径和登录例外正确；
- 业务接口的 `401/403` 语义未被放宽；
- 新增测试与完整回归通过；
- 真实 Swagger/JWT/HTTP 验收通过；
- 敏感信息、范围、清理和 `git diff --check` 通过；
- 未提前实现 Day 2～Day 7 能力。

## 13. 实施与验收结论

- Springdoc 2.8.17 已接入 Spring Boot 3.5.16 / Java 17，主代码 221 个源码文件编译成功。
- OpenAPI 提供 `FinGuard Core API` / `v1` 元数据、全局 `bearerAuth` HTTP Bearer/JWT Scheme；登录操作以空安全要求覆盖全局认证。
- 八组 Controller 已增加稳定 Tag 和操作摘要；`HttpServletRequest`、`Jwt` 等框架注入参数不会出现在客户端参数列表。
- 聚焦 `OpenApiHttpIntegrationTest` 为 4/4；完整 `mvn clean test` 为 370/370，均为 0 failures、0 errors、0 skipped。
- 真实 Java 17 应用 `/actuator/health` 为 `200/UP`；`/v3/api-docs` 返回 18 条路径，Swagger UI 与 swagger-config 均返回 200。
- 真实 ADMIN/REVIEWER 登录、账户查询为 200，ADMIN 审计查询为 200；匿名账户查询为 401，REVIEWER 创建账户和查询审计均为 403。
- 验收临时用户已删除且剩余 0，8080 端口已释放；Flyway 保持 V10，没有数据库迁移和 Day 2～Day 7 能力。

## 14. 提交建议

```text
feat: add OpenAPI documentation and lock week 6 contract
```
