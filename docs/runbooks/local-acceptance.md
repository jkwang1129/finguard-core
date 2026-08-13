# 本地六服务全链路验收 Runbook

## 目的与边界

本手册用固定隔离项目 `finguard-day7` 验证当前源码的构建、六服务健康、JWT/RBAC、CSV 异步导入、对账、风险审核、审计统计、Prometheus/Grafana 和应用重启恢复。脚本只清理自己创建的容器、网络、5 个命名卷、镜像和临时秘密。

不要把本手册用于普通开发栈，也不要手动对普通栈执行 `docker compose down -v`。

## 前置条件

1. `mvn -version` 显示 Maven 使用 Java 17。
2. Docker Engine 和 Compose Plugin 可访问。
3. 开发 MySQL、RabbitMQ、Redis 可以为 Maven 集成测试提供依赖；正式六服务验收另用独立端口和空卷。
4. 工作树范围明确，仓库中没有要保留的临时秘密或结果文件。

## 1. 查看无副作用执行计划

```powershell
& scripts/acceptance/invoke-week6-day7-acceptance.ps1 -DryRun | Format-List
```

确认 `ProjectName` 为 `finguard-day7`，服务集合为 app、MySQL、RabbitMQ、Redis、Prometheus、Grafana，并核对脚本声明的临时资源和清理动作。

## 2. 执行预检

```powershell
& scripts/acceptance/invoke-week6-day7-acceptance.ps1 -PreflightOnly | Format-List
```

预检必须确认 Maven JVM 为 Java 17、Docker/Compose 可用、开发与 Linux Compose 均能解析；它不会启动 Day 7 服务。

## 3. 运行完整验收

```powershell
$result = & scripts/acceptance/invoke-week6-day7-acceptance.ps1
$result | Format-List
```

必须同时得到：

| 字段 | 期望 |
| --- | --- |
| `ProjectName` | `finguard-day7` |
| `ServicesHealthy` | `True` |
| `FlywayVersion` | `11` |
| `OpenApiPathCount` | `18` |
| `PrometheusTargetUp` | `True` |
| `BusinessFlowPassed` | `True` |
| `RestartPassed` | `True` |
| `CleanupPassed` | `True` |

验收期间使用的端口为应用 18080、MySQL 13306、RabbitMQ AMQP 15674、RabbitMQ 管理 15673、Redis 16379、Prometheus 19090、Grafana 13000。出现端口占用、健康失败、非预期 401/403、数据事实不一致或清理失败时，脚本必须失败并进入自己的 `finally` 清理。

## 4. 独立检查残留

```powershell
$project = 'finguard-day7'
docker ps -aq --filter "label=com.docker.compose.project=$project"
docker volume ls -q --filter "label=com.docker.compose.project=$project"
docker network ls -q --filter "label=com.docker.compose.project=$project"
docker image ls -q --filter "reference=finguard-day7*"
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object LocalPort -in @(18080,13306,15674,15673,16379,19090,13000)
```

五条检查都应无输出。随后确认普通开发 MySQL、RabbitMQ、Redis 容器仍然存在；不要为了得到“零资源”而删除非 Day 7 容器。

## 5. 补充门禁

```powershell
& scripts/acceptance/tests/test-week6-day7-acceptance.ps1
& security/tests/test-security-tools.ps1
git diff --check
git status --short
```

详细的新鲜验收结果见 [Week 6 最终复盘](../review/week6-review.md)。
