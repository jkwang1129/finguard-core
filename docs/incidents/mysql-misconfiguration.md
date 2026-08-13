# MySQL 错误配置与恢复故障演练

## 性质与边界

- 性质：受控错误配置演练，不是生产事故。
- 日期：2026-08-12 至 2026-08-13（Asia/Shanghai）。
- 环境：固定隔离项目 `finguard-day6`。
- 扰动：以 `--no-deps` 启动一个 `MYSQL_PORT=1` 的一次性 app，不替换健康 app 或依赖。

## 影响与现象

一次性故障容器因数据库连接不可用按预期以 `exited|1` 退出，Compose 命令退出码为 1，有限日志包含数据源连接失败根因。原隔离应用、MySQL、RabbitMQ、Redis 和命名卷没有被修改；删除一次性容器后，原应用 health 保持 200。

## 时间线

1. 确认原隔离 app 和依赖健康。
2. 使用无效 MySQL 端口启动一次性 app，且不自动启动或重建依赖。
3. 等待容器退出，记录 `exited|1`、Compose 退出码和有限日志。
4. 确认失败发生在数据源/Flyway 建立连接阶段，没有业务写入。
5. 先 inspect 一次性容器；只在它存在时删除，保证清理幂等。
6. 查询原应用 health=200，确认健康实例和依赖未被替换。

## 根因

一次性容器收到无效数据库端口，数据源无法建立 MySQL 连接，Spring 上下文在启动阶段快速失败。由于使用了独立容器且 `--no-deps`，故障没有传播到健康 app 或依赖服务。

首次演练还发现清理逻辑会把“不存在旧容器”的 Docker 错误当成致命异常。修正后先检查容器存在性，仅删除明确命中的 Day 6 容器，使重复清理安全。

## 恢复与验证

恢复动作只移除一次性故障容器。必须确认原应用 health=200、MySQL schema 仍为预期版本、正常容器未重建、命名卷未删除，并清理临时环境文件。

如果出现 Flyway 校验失败、schema 兼容性不明或数据完整性不确定，不得编辑历史迁移或删除卷，应停止并按数据恢复/前向修复流程处理。

## 预防

- 部署前执行 Compose 静态解析、环境文件校验和依赖健康检查。
- 错误配置诊断使用一次性、无依赖重建的容器，不直接修改保留环境文件。
- 清理逻辑必须幂等，并只接受固定的隔离容器名。
- 应用启动依赖失败必须保持快速失败，不以吞掉连接异常换取假健康。

## 证据

- [Day 6 故障演练总复盘](../review/week6-day6-fault-drills.md)
- [MySQL 错误配置演练脚本](../../scripts/drills/invoke-misconfiguration-drill.ps1)
- [Linux 部署与回滚 Runbook](../runbooks/linux-deployment-and-rollback.md)
- [依赖故障响应 Runbook](../runbooks/dependency-incident-response.md)
