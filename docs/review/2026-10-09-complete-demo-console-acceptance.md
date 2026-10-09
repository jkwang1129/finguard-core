# 完整业务控制台验收 — 2026-10-09

工作分支 `codex/interactive-live-demo`，目录 `C:/dev/worktrees/finguard-core-interactive-live-demo`。实现覆盖 27 个业务操作、29 个场景配方与工程证据入口，映射见 [覆盖清单](../DEMO_COVERAGE.md)。本报告只对明确列出的环境和运行结果负责。

## 已核对的结果

| 检查 | 实际结果 |
| --- | --- |
| Java 17 / Maven / 真实隔离中间件 | 审查修复后再次 `mvn.cmd -B -ntp clean test`，404 测试，失败/错误/跳过均为 0，2026-10-09 20:49:07 +08:00 |
| Node 24.14.1 原生模块回归 | `node --test scripts/demo/tests/*.test.mjs`，28/28 |
| PowerShell runner 隔离契约 | 7 个断言通过；资源/端口冲突预检、随机临时凭据、限定清理目标 |
| 默认环境真实浏览器业务 | 审查修复后同一轮 7 个工作区用例 + 29 场景 + 6 可靠性 + 2 可用性，44 PASS |
| 390px 布局回归 | 实际浏览器发现根网格最小宽度导致 729px 溢出；CSS 修正后页面宽度与视口均为 390px |
| 两配置变体 | 关闭三规则 3 PASS；阈值 50000.00 变体 1 PASS；与默认 44 项合计 48 项，最终运行结束于 2026-10-09 20:53:15 +08:00 |
| 审查定向浏览器回归 | 六项 RED→GREEN：HTTP 来源、账户/交易详情竞争、毫秒交易保存、配方替换、审核筛选；不计入上述 48 项真实后端验收 |
| Redis 故障恢复 | 基线 health/statistics 200；故障 health 503，登录/statistics 200；恢复 health/statistics 200 |
| MQ 重试与死信 | 2 个真实测试，失败/错误/跳过均为 0；RetryAttempt=2，FailureCode=RETRY_EXHAUSTED |

默认浏览器结果包含真正的 HTTP、任务终态、原因码、风控命中、权限拒绝与审核结果。29 场景使用真实浏览器中的产品场景模块调用实际 API；工作区 CRUD 与单步 GUIDED 闭环另外通过界面交互验证，并非声称 29 场景的每个按钮均逐一人工点击。101 条账户分页、第二页批次审计、重复点击仅一次写入、迟到响应隔离、GET 超时、刷新重新认证和凭据不持久化均有独立断言。

脱敏证据：[最终默认环境 44 项](evidence/2026-10-09-console/browser-baseline.json)、[关闭规则](evidence/2026-10-09-console/browser-rules-disabled.json)、[非默认阈值](evidence/2026-10-09-console/browser-high-threshold.json)、[Redis 演练](evidence/2026-10-09-console/redis-drill.json)、[MQ 演练](evidence/2026-10-09-console/messaging-drill.json)、[业务环境](evidence/2026-10-09-console/console-environment.json)、[工程环境](evidence/2026-10-09-console/engineering-environment.json)、[桌面截图](evidence/2026-10-09-console/desktop.png)、[390px 截图](evidence/2026-10-09-console/mobile-390.png)。初次验收的 42+2 分次记录保留在 [审查前记录](evidence/2026-10-09-console/browser-baseline-before-review.json)，最终 44 项来自单次完整分组运行 `78143e11f9824875b12669a9e0fc2611`。证据 JSON 不包含密码、JWT 或环境文件正文。原有开发 MySQL/RabbitMQ/Redis 保持运行，本次两类独立栈的容器、卷、网络、镜像标签和临时环境文件均已清理。

## 环境与复现

测试使用 MySQL/RabbitMQ/Redis Testcontainers，不使用开发 `.env`。六服务业务栈使用 `finguard-console` 和端口 28080/23306/25672/25673/26379/29090/23000。故障演练单独使用 `finguard-day6`，与业务栈及原有开发容器分离。结束时按捕获的资源 ID 和 Compose 标签核对并清理自身资源及临时凭据。

```powershell
cd scripts/demo
npm ci
npx playwright install chromium
cd ../..
powershell -NoProfile -File scripts/demo/invoke-console-acceptance.ps1
powershell -NoProfile -File scripts/demo/invoke-console-acceptance.ps1 -EngineeringOnly
```

本次使用 `-UseCachedRuntime -BrowserChannel chrome`：宿主 Java 17 打包当前 JAR，并使用本地缓存 JRE 的固定 digest 构建测试镜像；Playwright 1.64.0 驱动已安装 Chrome。生产 Dockerfile 构建仍是 runner 默认路径，但本次没有验证重新下载基础镜像的生产构建路径。Chromium CDN 下载遭遇重复 SSL MAC 错误，浏览器替换及打包替换不改变产品依赖。

实际 Chrome 版本为 `155.0.8059.39`；JRE digest 为 `eclipse-temurin@sha256:c0a0a9c12ecf8c31c32fd19a1a81ef82a2e22f54186ac33dbe5a1964d76574d8`。完整测试命令的脱敏结果摘要：[Java](evidence/2026-10-09-console/java-summary.txt)、[Node](evidence/2026-10-09-console/node-summary.txt)。

默认栈明确启用三规则并设置 10000.00 / 5m / 10m 内 5 笔支出。变体关闭全部规则及将大额阈值升至 50000.00，只按服务端实际返回判定；页面的默认阈值说明不冒充当前服务器配置。为批量验收提高登录和上传限额至 1000，限流行为由真实 Redis 后端回归负责。

## 证据边界与审查

Health、指标、监控可达性、业务终态和上述两项故障演练是当前运行证据。压力测试、安全扫描、CI、部署与回滚是标注时间和环境的历史复现入口；本次没有新的性能、安全扫描、远端 CI 或部署结论。

保存旧版本后重复决定的入口展示真实终态拒绝；`VERSION_CONFLICT` 配方在 PENDING 状态提交不一致版本，单独证明 REVIEW_VERSION_CONFLICT。这不证明不存在的“编辑 PENDING 审核任务”并发 API。

一次独立全分支审查及一轮必要修复已完成，见 [审查记录](2026-10-09-console-independent-review.md)。四个原 Important 问题及升级处理的两个配方/筛选问题已修复；仍有一个按钮状态的 Minor 暂缓，终态守卫不会发出再次写入。修复后完整回归通过，完整范围 `git diff --check` 通过。分支尚未推送、合并或部署。
