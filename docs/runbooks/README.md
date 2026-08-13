# FinGuard Core 运维操作手册

本目录面向项目维护者，提供可重复、受边界保护的本地验收、Linux 部署/回滚和依赖故障响应步骤。产品范围见 [PRD](../PRD.md)，整体部署模型见 [部署文档](../DEPLOYMENT.md)。

## 使用前提

- 先确认当前分支、目标完整 Git SHA 和工作树状态。
- 本地构建/测试使用 Maven 实际运行的 Java 17。
- Linux 操作由部署用户执行，Docker Engine 与 Compose Plugin 必须可用。
- 所有密码、JWT 密钥、数据库、RabbitMQ、Redis、Grafana 和 GHCR 凭据只通过受保护环境注入，不写入命令历史或文档。
- 执行会改变状态的步骤前，先阅读对应手册的停止条件和清理边界。

## 手册选择

| 场景 | 手册 |
| --- | --- |
| 在独立空卷中验证完整项目 | [本地六服务验收](local-acceptance.md) |
| 首次部署、升级、停止或回滚 Linux 环境 | [Linux 部署与回滚](linux-deployment-and-rollback.md) |
| Redis、RabbitMQ 或 MySQL 异常 | [依赖故障响应](dependency-incident-response.md) |

配套参考：[监控与排障](../MONITORING.md)、[故障演练记录](../incidents/README.md)、[Week 6 最终复盘](../review/week6-review.md)。

## 安全边界

- 破坏性本地清理只能命中固定 Compose project `finguard-day7` 及其明确拥有的资源。
- 禁止对普通开发栈或保留部署执行 `docker compose down -v`。
- Linux 日志只允许读取白名单服务的有限尾部，不使用无限跟随。
- 日常停止和应用回滚不删除命名卷。
- 无法确认数据完整性、目标镜像、迁移兼容性或清理范围时立即停止并升级处理。
