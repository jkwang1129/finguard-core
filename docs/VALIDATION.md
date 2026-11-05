# 测试与验证

## 自动化测试

Java 17、Maven 3.9+ 与 Docker Engine 可用时，在仓库根目录执行：

```powershell
mvn.cmd -B -ntp clean test
node --test scripts/demo/tests/*.test.mjs
powershell -NoProfile -File scripts/demo/tests/local-launcher.test.ps1
```

Java 测试使用 Testcontainers 创建隔离的 MySQL、RabbitMQ、Redis，覆盖业务事务、幂等、权限、消息重试、审核并发和缓存降级。前端测试覆盖请求、会话、工作区、场景与迟到响应隔离。

## 真实浏览器验收

```powershell
npm.cmd ci --prefix scripts/demo
node scripts/demo/node_modules/playwright/cli.js install chromium
powershell -NoProfile -File scripts/demo/invoke-console-acceptance.ps1 -PreflightOnly
powershell -NoProfile -File scripts/demo/invoke-console-acceptance.ps1
```

默认使用独立 `finguard-console` 六服务栈、随机临时凭据与独立端口，结束时核对资源 ID 和 Compose 标签后清理自身资源。端口、依赖或资源冲突会中止运行。

已安装 Chrome 且本地 JRE 已缓存时，可增加 `-UseCachedRuntime -BrowserChannel chrome`；该路径不代替生产 Dockerfile 的构建验证。

独立 Redis / MQ 演练使用另一隔离栈：

```powershell
powershell -NoProfile -File scripts/demo/invoke-console-acceptance.ps1 -EngineeringOnly
```

两类验收按顺序运行。故障注入、恢复和资源边界见 [故障演练](incidents/README.md)。

## 六服务全链路验收

```powershell
powershell -NoProfile -File scripts/acceptance/invoke-local-acceptance.ps1 -DryRun
powershell -NoProfile -File scripts/acceptance/invoke-local-acceptance.ps1 -PreflightOnly
powershell -NoProfile -File scripts/acceptance/tests/test-local-acceptance.ps1
powershell -NoProfile -File scripts/acceptance/invoke-local-acceptance.ps1
```

该入口验证业务闭环、监控、应用重启与隔离资源清理，操作边界见 [本地验收手册](runbooks/local-acceptance.md)。与控制台验收使用不同项目名，但端口有重合，不能同时运行。

## 历史验证范围

下列结果整理自对应日期的运行记录，仅表示当时的版本与环境；不代表当前提交已经重跑。

| 日期 | 范围 | 历史结果与边界 |
| --- | --- | --- |
| 2026-10-09 | 后端与前端回归 | Java 404/404、Node 28/28 |
| 2026-10-09 | 控制台真实后端浏览器验收 | 默认环境 44 项、规则配置变体 4 项通过；另有六项定向浏览器回归，不计入这 48 项 |
| 2026-10-09 | Redis / MQ | Redis 中断、降级与恢复；MQ 有限重试和死信验证通过 |
| 2026-08-12 至 2026-08-13 | 性能、安全与部署 | 见 [性能报告](PERFORMANCE_REPORT.md)、[安全摘要](SECURITY_TEST_REPORT.md) 和 [部署手册](DEPLOYMENT.md) |

上述浏览器验收采用宿主 Java 17 打包与本地缓存 JRE、已安装 Chrome；未在同轮验证重新下载基础镜像的生产构建路径，也未新增远端 CI、部署、性能或安全扫描结论。运行后的计数和健康状态应以本次输出为准。
