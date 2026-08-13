# ADR-0005：使用完整 Git SHA 不可变镜像部署

日期：2026-08-13

## 状态

Accepted

## 背景

部署和回滚需要知道运行的确切代码。`latest` 或可覆盖版本标签无法可靠对应提交，可能在相同命令下拉到不同内容，也无法为验收和故障记录提供稳定身份。Linux 环境还必须在不删除数据库、消息、缓存和监控卷的情况下切换应用版本。

## 决策

GitHub Actions 仅在 Java 测试、打包和镜像检查通过后，将应用镜像以完整 40 位 Git SHA 发布到 GHCR。Linux `.env.linux` 指定完整 SHA 镜像；部署脚本使用 `--no-build` 拉取并等待健康。成功部署记录 current/previous image，回滚只原子切换应用镜像并复用部署流程，5 个命名卷保持不变。

## 理由

- 完整 SHA 将源码、CI run、镜像和部署证据连接为同一不可变身份。
- 不在服务器构建，减少环境漂移和未测试代码进入运行环境的机会。
- 保留卷允许应用升级/回滚后继续验证业务与监控事实。

## 后果

每次提交会产生独立镜像，需要 Registry 生命周期管理。回滚只能恢复应用二进制，不回滚 Flyway、业务数据、消息或外部副作用；发布不兼容迁移前必须另行设计前后兼容、备份和恢复。

## 替代方案

- **使用 `latest`**：简单但不可追溯、不可重复，拒绝采用。
- **服务器现场构建**：增加工具链、源码和依赖漂移，不符合不可变交付。
- **回滚时删除数据卷**：会造成数据丢失，任何日常部署/回滚都禁止。

## 证据

- [Linux 部署设计](../design/week6-day4-linux-deployment-design.md)
- [Linux 部署验收](../review/week6-day4-linux-deployment-acceptance.md)
- [部署与回滚文档](../DEPLOYMENT.md)
- [Linux 部署与回滚 Runbook](../runbooks/linux-deployment-and-rollback.md)
