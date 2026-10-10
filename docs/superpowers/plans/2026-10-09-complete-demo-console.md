# FinGuard Core 完整业务控制台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 为现有 demo 补齐全部业务操作、业务分支场景、工程演示入口，并交付可核对的浏览器与运行证据。

**Architecture:** Spring Boot 提供 `/demo/index.html`、样式和原生 ES modules；工作区共用身份、API、分页与取消机制。复用现有身份和审核上下文分支，新增两条历史集合 GET；既有写入、风险和权限语义保持设计规定的行为。

**Tech Stack:** Java 17、现有 Spring Boot/MyBatis-Plus/MySQL/RabbitMQ/Redis；原生 HTML/CSS/JavaScript；JUnit/MockMvc；Node 内建测试；仅测试用 Playwright。

**Spec:** [已确认设计](../specs/2026-10-09-complete-demo-console-design.md)，提交 `394788d`。

**状态:** Tasks 1–12、一次独立全分支审查及一轮重要问题修复均完成。最终 Java 404/404、Node 28/28、真实浏览器 48/48 与六项定向回归通过；见[验收报告](../../review/2026-10-09-complete-demo-console-acceptance.md)。分支留待集成，未推送或部署。

## Global Constraints

- 复用 `C:/dev/worktrees/finguard-core-interactive-live-demo` 和分支 `codex/interactive-live-demo`，开始时核对状态；不改当前主目录的未提交文件。
- 继续提供 `/demo/index.html`，无需独立前端服务器、框架或产品构建依赖。
- Token 与密码不进入 localStorage、sessionStorage、Cookie、URL、日志或导出内容。
- 页号从 1 起，默认页大小 20，上限 100；按 `created_at DESC, id DESC` 稳定排序。
- POST/PATCH/PUT/DELETE 不自动重试；轮询默认每秒一次、上限 90 秒；202 只代表已受理。
- 首次业务写入需要数据持久化确认；删除、停用展示目标及后果并确认。
- 大额默认 10000.00、疑似重复默认 5 分钟、高频默认 10 分钟内 5 笔支出，仅标为默认配置。
- 浏览器不取得 Docker、数据库或服务器凭据；不新增远程 shell 或停服务 API。
- 所有接口文本通过安全 DOM API 渲染；历史报告标明日期与环境，不作为当前运行成功证据。
- 回归与浏览器写入仅使用隔离资源；清理须验证资源所有权，不对共享开发数据库执行删卷或批量清理。
- 实现按任务持续推进；记录具体证据、限制及调整依据。完成代码后再报告运行验收；本计划审阅不等于任何测试已经通过。

## Review Focus

1. 快速切换任务或角色时，晚到的只读响应不能覆盖当前任务，注销后不能继续使用旧 Token（Task 4、12）。
2. 重复点击、断线、轮询超时不能导致隐式重放写入；主动幂等演示必须由单独按钮明确发起（Task 4、7、8）。
3. 超过 100 条和同时间戳记录必须可以正确分页；全批次审计不得只过滤第一页（Task 3、10、12）。
4. 空列表、非法 ID、CSV BOM/换行及十进制金额必须有明确结果；任意文本不能执行 HTML（Task 5、6、7、12）。
5. 关闭规则、非默认阈值、监控不可达及环境故障不能产生虚假的命中或“已验证”标记（Task 11、12）。

## 文件与共用接口

所有仓库路径相对于上述工作目录。现有 Java 路径基址 `src/main/java/com/finguard/core/`，Java 测试基址 `src/test/java/com/finguard/core/`。下文分别简称 **J** 和 **JT**；执行者据此展开为确切路径。

前端目录 **D** = `src/main/resources/static/demo/`。测试工具目录 **T** = `scripts/demo/`。产品脚本不从 T 导入依赖。

| 文件组 | 职责 |
| --- | --- |
| `J/auth/*`、`J/review/*` | 合入已有身份与上下文代码，保留角色约束 |
| `J/importjob/dto/ImportJobQueryRequest.java`、现有 Controller/Service/Mapper | 导入历史查询 |
| `J/reconciliation/dto/ReconciliationJobQueryRequest.java`、现有 Controller/Service/Mapper | 对账历史查询 |
| `D/index.html`、`D/styles.css`、`D/js/app.js` | 页面骨架、导航、模块生命周期 |
| `D/js/api.js`、`D/js/session.js`、`D/js/ui.js` | 请求、会话、轮询、安全 DOM 和分页 |
| `D/js/workspaces/*.js`、`D/js/scenarios.js` | 七个工作区、原逐步演示、场景配方 |
| `T/tests/*.test.mjs` | 无真实写入的客户端行为测试 |
| `T/browser-acceptance.mjs`、`T/browser-cases/*.mjs` | 真实浏览器验收与可重跑案例 |
| `T/invoke-console-acceptance.ps1` | 隔离六服务、浏览器运行、证据与清理 |
| `docs/DEMO_COVERAGE.md`、`docs/review/2026-10-09-complete-demo-console-acceptance.md` | 接口/场景/工程覆盖与本次结果 |

JavaScript 契约（后续任务使用同名函数）：

```text
ApiResult = {status:number, data:unknown, retryAfter:string|null}
ApiError = Error & {status:number, code:string, fieldErrors:unknown, retryAfter:string|null}
request(path, {method='GET', role, json, form, signal}={}) -> Promise<ApiResult>
pollJob(path, {role, terminalStatuses, signal, onUpdate, timeoutMs=90000, intervalMs=1000}) -> Promise<ApiResult>
readAllPages(path, {role, query={}, signal, onPage}={}) -> Promise<Array>
login(role, username, password, {signal}={}) -> Promise<{username,roles}>
logout(role) -> void; identity(role) -> {username,roles}|null
authorization(role) -> string|null; subscribeSession(listener) -> unsubscribe()
mount(root:HTMLElement, context:WorkspaceContext) -> {dispose():void}
WorkspaceContext = {request, pollJob, readAllPages, identity, navigate, confirmWrite, signal}
navigate(workspace:string, {id, accountId, importJobId}={}) -> void
confirmWrite({action, target, destructive=false}) -> Promise<boolean>
renderPage(root, pageResponse, {onPage, onSize}) -> void
setFeedback(root, {message, status, code, fieldErrors}) -> void
buildScenario(name, {runId, anchorTime, amount, offsetDays}) -> ScenarioPlan
executeScenarioStep(plan, stepIndex, context) -> Promise<StepEvidence>
```

`session.js` 用注入的认证传输避免与 `api.js` 循环依赖；Token 留在模块闭包。注销通过会话订阅通知 app 取消全部所属读请求。`app.js` 每次 mount 创建 AbortController 与递增 generation，dispose 同时取消并拒绝过期渲染。

## 验证命令约定

在工作目录使用 `mvn.cmd`；已检查其 Java 为 17.0.10、Node 为 24.14.1。长输出写入本计划专属工作区日志，仅读取结尾和报告汇总。

- Java：`mvn.cmd -B -ntp "-Dtest=<类名列表>" test`；预期 BUILD SUCCESS、失败/错误均为 0，不把环境错误视为功能断言通过。
- JS：`node --test scripts/demo/tests/<name>.test.mjs`；预期 fail 0。
- 语法：`node --check <确切 JS 文件>`；预期退出 0。
- 静态：`git diff --check`；预期无错误。
- 浏览器：`node scripts/demo/browser-acceptance.mjs --base-url http://127.0.0.1:28080 --cases <分组>`；使用隔离 runner 设置的进程凭据，禁止打印凭据或 Token。

浏览器工具依赖仅在 T 的私有 `package.json` 与 lockfile 管理。执行时安装并锁定实际采用的 Playwright 版本；直接运行 `node scripts/demo/node_modules/playwright/cli.js install chromium`，避开历史 Windows npx 启动问题。Node 内建测试无需该依赖。

### Task 1: 建立隔离回归基线

**Files:** 修改 `pom.xml`、`src/test/resources/application.properties`；创建 `JT/testsupport/FinGuardTestContainers.java`、`JT/testsupport/TestcontainersEnvironmentPostProcessor.java`、`src/test/resources/META-INF/spring.factories`、`JT/testsupport/TestcontainersIsolationIntegrationTest.java`。

**Interfaces:** 复用主目录已有但未提交的两个 testsupport 文件作为经过核对的来源；产出 `FinGuardTestContainers.springProperties(): Map<String,Object>` 与所有 Spring 测试自动使用的随机端口中间件。仅测试作用域增加现有主目录采用的三个 Testcontainers 依赖。

- [x] 写 `containerPropertiesOverrideLocalAddresses`：断言来源名 `testcontainers` 存在，MySQL/RabbitMQ/Redis 端口来自容器；新测试中设置互相冲突的 localhost 属性，断言容器地址获胜。断言测试 bootstrap 关闭且测试库没有演示账号。
- [x] 运行 `mvn.cmd -B -ntp "-Dtest=TestcontainersIsolationIntegrationTest" test`，确认新隔离断言失败；若缺少 Docker，记录环境失败并恢复 Docker，不能称为预期 RED。
- [x] 只复制并审阅主目录相应 Java 支持文件和 spring.factories；在本工作目录编辑 POM 的 test scope 依赖。测试属性显式关闭 bootstrap，保留当前测试 JWT key 及消费者设置，不复制 `.env` 或演示口令。
- [x] 再运行该测试，预期全部通过；运行 `mvn.cmd -B -ntp clean test`，记录基线实际总数与结果。容器由 Ryuk 回收。
- [x] 用 `git diff --check` 核对后提交 `test: isolate console regression middleware`；仅暂存此任务文件。

### Task 2: 合入身份与审核上下文

**Files:** 合入提交 `d25770a` 的 12 个文件：`J/auth/config/SecurityConfiguration.java`、`J/auth/controller/AuthController.java`、`J/auth/vo/CurrentUserResponse.java`、`J/review/controller/ReviewTaskController.java`、`J/review/mapper/ReviewTaskMapper.java`、`J/review/model/ReviewTaskContextView.java`、`J/review/service/ReviewTaskService.java`、`J/review/service/impl/ReviewTaskServiceImpl.java`、`J/review/vo/ReviewTaskContextResponse.java`、`JT/OpenApiHttpIntegrationTest.java`、`JT/auth/AuthCurrentUserHttpIntegrationTest.java`、`JT/review/ReviewTaskContextHttpIntegrationTest.java`。

**Interfaces:** 产出 `GET /api/auth/me -> CurrentUserResponse(username,roles)`；`GET /api/review-tasks/{id}/context -> ReviewTaskContextResponse`，只允许 REVIEWER。

- [x] 先读取原提交与测试，增加跨分支权限回归断言：匿名 `/demo/index.html` 200；匿名 me 401；ADMIN context 403；REVIEWER context 可读；ADMIN decision 403。
- [x] 在未合入接口时运行身份/上下文新增断言，确认缺失路由或权限断言失败，旧 demo 仍通过。
- [x] `git cherry-pick d25770a`；如冲突，按已确认设计同时保留匿名 GET `/demo/**` 和新增认证/上下文权限，保留 Task 1 支持文件。
- [x] 运行 `mvn.cmd -B -ntp "-Dtest=AuthCurrentUserHttpIntegrationTest,ReviewTaskContextHttpIntegrationTest,DemoStaticResourceSecurityIntegrationTest,OpenApiHttpIntegrationTest" test`，预期全通过。
- [x] 将新增跨分支回归断言与冲突解决说明提交为 `test: verify combined demo and read-contract permissions`；已有提交复用不重写业务代码。

### Task 3: 两条历史集合查询

**Files:** 创建 `J/importjob/dto/ImportJobQueryRequest.java`、`J/reconciliation/dto/ReconciliationJobQueryRequest.java`、`JT/importjob/ImportJobHistoryHttpIntegrationTest.java`、`JT/reconciliation/ReconciliationJobHistoryHttpIntegrationTest.java`；修改两模块已有 Controller、Service、ServiceImpl、Mapper，以及 `J/auth/config/SecurityConfiguration.java`、`JT/OpenApiHttpIntegrationTest.java`、`docs/API.md`。

**Interfaces:** `ImportJobService.query(ImportJobQueryRequest) -> PageResponse<ImportJobResponse>`；`ReconciliationJobService.query(ReconciliationJobQueryRequest) -> PageResponse<ReconciliationJobResponse>`。Mapper 使用既有分页模型 `IPage<Entity> selectHistory(Page<Entity>, filters...)`，不查询原文件字节。

- [x] 写 `historySupportsStablePaginationAndFilters`、`historyRejectsInvalidParameters`、`historyEnforcesReadRoles`。断言默认 page=1/size=20；size=101、page=0、createdBy=0 返回 400；匿名 401，两角色 200；101 个同 createdAt 记录翻页 ID 无重叠且并集为 101。
- [x] 运行两新测试，预期集合 GET 缺失而失败。
- [x] 实现设计第 6 节精确参数与 `created_at DESC,id DESC` 排序。GET 列表的两个 duplicate 标记为 false；空列表返回 total=0、records=[]，非法枚举 400；授权规则只增加集合 GET。
- [x] OpenAPI 断言 `/api/import-jobs`、`/api/reconciliation-jobs` 同时含 get/post，保留写权限。API 文档计数最终为 27 个业务操作、20 个路径对象，按实际 OpenAPI 再核对。
- [x] 运行 `mvn.cmd -B -ntp "-Dtest=ImportJobHistoryHttpIntegrationTest,ReconciliationJobHistoryHttpIntegrationTest,OpenApiHttpIntegrationTest,RbacAuthorizationIntegrationTest" test`，预期全通过。
- [x] 提交 `feat: add import and reconciliation history queries`。

### Task 4: 共用客户端、会话与控制台外壳

**Files:** 创建 `D/guided-legacy.html`（迁移期间保留原页面）、`D/styles.css`、`D/js/api.js`、`D/js/session.js`、`D/js/ui.js`、`D/js/app.js`、`T/tests/api.test.mjs`、`T/tests/session.test.mjs`、`T/tests/lifecycle.test.mjs`；修改 `D/index.html`、`JT/DemoStaticResourceSecurityIntegrationTest.java`。

**Interfaces:** 产出上述完整 JS 契约；在模块未逐项迁移前继续保留可访问的原逐步演示，不移除其功能。新 `index.html` 注册七个工作区，尚未实现区明确显示建设状态，最终 Task 12 清除这些状态。

- [x] 写失败测试断言：401 与轮询超时不同；失败写请求只 fetch 一次；202 后仅 GET；200 duplicate 返回原 ID；90000ms 上限保留最后状态；AbortError 不渲染为服务器失败；注销导致旧请求取消且身份清空。
- [x] 写 generation 测试：A 任务请求晚于 B 返回，当前 DOM 只显示 B；role 切换取消 A。`readAllPages` 读取 total=101/size=100 的第二页，非法分页结构报错而不静默截断。
- [x] 用 Node 内建 test 执行上述三文件，预期缺失模块或行为断言失败。
- [x] 实现客户端与页面外壳。session 注入 transport；权限使用 me.roles；新导航 hash 仅含白名单工作区与正整数 ID。表单反馈/记录用 textContent，响应内容不全量写日志。
- [x] 保留旧页面到guided-legacy并提供临时入口；共用context请求默认绑定当前signal，返回后再次检查取消状态。静态集成测试将原来的内联函数源码断言改为HTML入口、资源可读及权限契约；功能断言转交Node/浏览器测试。
- [x] 运行三份 Node 测试及 `DemoStaticResourceSecurityIntegrationTest`，断言新样式及模块可匿名 GET、受保护 API 仍返回正确状态。
- [x] 提交 `feat: add modular demo console and authenticated sessions`。

### Task 5: 账户工作区

**Files:** 创建 `D/js/workspaces/accounts.js`、`T/tests/accounts.test.mjs`；修改 `D/js/app.js`。

**Interfaces:** 使用 `mount(root,context)`；调用设计 5.1 的六个操作；`navigate('transactions',{accountId})` 关联账户交易。

- [x] 写测试：查询请求带 page/size/keyword/status/accountType；改变筛选回到 page=1；提交改名/启停/删除采用正确方法与路径；取消确认不发送请求。
- [x] 运行账户测试，预期工作区不存在或行为失败。
- [x] 实现列表、详情、创建、编辑、启停、删除、翻页及关联跳转。删除解释必须已停用且无交易历史；后端拒绝时显示真实 409。REVIEWER 隐藏或禁用写入口并解释角色。
- [x] 对 `accountName='<img src=x onerror=...>'` 用安全渲染契约断言仅显示文本；空列表与404展示明确反馈。
- [x] 运行账户与共用 Node 测试，执行 JS 语法检查；提交 `feat: add account management workspace`。

### Task 6: 交易工作区

**Files:** 创建 `D/js/workspaces/transactions.js`、`T/tests/transactions.test.mjs`；修改 `D/js/app.js`。

**Interfaces:** 使用共用 mount 与账户跳转参数；产生请求的金额为字符串，时间为接口 LocalDateTime，不直接序列化为 UTC Instant。

- [x] 写测试：amount='0.10' 提交后保持 '0.10'；账户筛选来自 hash；来源 CSV 时不出现人工修改按钮；PUT 包含现有 UpdateTransactionRequest 全部字段；时间范围和分页正确。
- [x] 运行交易测试，预期缺失工作区或金额/契约断言失败。
- [x] 实现五个操作及全部 TransactionQueryRequest 筛选；详情显示来源和关联账户，注明业务时区；400字段错误保留原表单，删除要求确认。
- [x] 覆盖空列表、非法 ID、非数字金额和网络失败；既有导入交易限制由服务器最终验证。
- [x] 运行交易与共用 Node 测试及语法检查；提交 `feat: add transaction management workspace`。

### Task 7: 导入历史、任意文件、行错误与幂等

**Files:** 创建 `D/js/workspaces/imports.js`、`T/tests/imports.test.mjs`；修改 `D/js/app.js`。

**Interfaces:** 历史使用 Task 3 的 GET；文件上传 request(form)；`pollJob` 终态 SUCCESS/PARTIAL_SUCCESS/FAILED；`navigate('reconciliation',{importJobId})`。

- [x] 写测试：FormData 使用 file part 且不手工设 multipart Content-Type；BOM/CRLF 预览可读、发送保留原字节；PARTIAL_SUCCESS 展示计数/错误入口；FAILED 不自动对账；分页行错误无假筛选。
- [x] 加重复按钮断言：普通双击仅上传一次，主动点击重复验证再发送一次完全相同字节并比较 ID/duplicateFile；超时继续查询只 GET。
- [x] 运行导入测试，确认缺失模块或行为失败。
- [x] 实现文件预览、持久化确认、历史筛选分页、ID详情、行错误分页、取消轮询、主动重复验证；hash 找回任务不需要原文件。
- [x] 运行导入与 API 测试及语法检查；提交 `feat: add import history and error investigation`。

### Task 8: 对账历史与逐笔结果

**Files:** 创建 `D/js/workspaces/reconciliation.js`、`T/tests/reconciliation.test.mjs`；修改 `D/js/app.js`。

**Interfaces:** 创建体仅 `{importJobId}`；COMPLETED/FAILED 终态；结果仅服务端 resultType 筛选；关联交易通过 navigate 定位。

- [x] 写测试：失败/未完成导入不可发起；202保留ID并GET到终态；主动重复返回原 ID；结果 page=2/size=20/resultType=DUPLICATE 发出正确查询；方法与原因码只展示不假冒全局筛选。
- [x] 运行对账测试，确认缺失工作区或行为失败。
- [x] 实现历史列表、任务恢复、四类计数、时间与错误、逐笔分页及关联详情。改变任务取消旧轮询，旧响应无法覆盖新选择。
- [x] 运行对账/API/lifecycle Node 测试及语法检查；提交 `feat: add reconciliation investigation workspace`。

### Task 9: 审核上下文与版本化决定

**Files:** 创建 `D/js/workspaces/reviews.js`、`T/tests/reviews.test.mjs`；修改 `D/js/app.js`。

**Interfaces:** REVIEWER 读取 Task 2 的 context；决定 body `{decision:'CONFIRMED'|'IGNORED',version:number,note:string|null}`。列表采用原 ReviewTaskQueryRequest。

- [x] 写测试：ADMIN不调用context也不提交decision；REVIEWER使用返回version；409保留note且不重试；reload更新version；终态隐藏决定；resultType/ruleCode互斥筛选不产生非法组合。
- [x] 运行审核测试，确认缺失模块或行为失败。
- [x] 实现任务列表、详情、上下文、关联导航、备注和两种决定；保留旧版本演示入口仅针对明确选择的演示任务，显示旧版本和实际拒绝结果。
- [x] 运行审核/API/session Node 测试及语法检查；提交 `feat: add contextual review decisions and conflict handling`。

### Task 10: 完整审计与全库统计

**Files:** 创建 `D/js/workspaces/insights.js`、`T/tests/insights.test.mjs`；修改 `D/js/app.js`。

**Interfaces:** 审计使用 page/size/actionCode/initiatedBy；批次模式消费 `readAllPages` 后根据已知 importJobId/reconciliationJobId/reviewTaskId 筛选；统计来自 overview。

- [x] 写测试：匹配批次日志只存在于第二页时仍显示；主动取消遍历停止GET且标记未完整加载；ADMIN才请求audit；两角色均读overview；全库统计注明范围与采集时间。
- [x] 运行摘要测试，确认缺失模块或遗漏第二页断言失败。
- [x] 实现筛选、分页、详情与关联跳转，批次遍历展示已读页数与取消；用业务卡片替代只有原始JSON，保留可展开安全字段供核对。
- [x] 运行摘要/API Node 测试及语法检查；提交 `feat: add complete audit browsing and database statistics`。

### Task 11: 全业务配方、原演示迁移与工程证据区

**Files:** 创建 `D/js/scenarios.js`、`D/js/workspaces/guided.js`、`D/js/workspaces/engineering.js`、`T/tests/scenarios.test.mjs`、`T/tests/engineering.test.mjs`；修改 `D/index.html`、`D/js/app.js`、`docs/DEMO.md`。

**Interfaces:** `ScenarioPlan={name,runId,expected,steps}`；步骤描述现有创建/上传/对账/审核操作及产出ID。`StepEvidence={taskIds,states,httpStatus,actual,observedAt}`。工程模块 GET health/prometheus；提供可配置 http/https 监控链接与原脚本步骤，不代理任意URL。

- [x] 写场景测试：所有设计场景均有配方；EXACT两侧强键相同；弱匹配外部流水号不同；多候选至少两人工候选；已占用配方先强键预留；风险高频构造5笔支出、疑似重复构造相同账户/方向/金额但不同业务键；CSV输出金额不经浮点累加。
- [x] 写工程测试：503与网络不可达显示实际状态；空指标不标成功；历史报告带日期；非默认阈值或关闭规则的实际无命中不被前端改写；监控配置拒绝javascript:；导出只含设计的字段白名单。
- [x] 运行两个Node文件，预期缺失模块或语义断言失败。
- [x] 实现可预览、单步执行的全部配方；原逐步演示迁移到guided模块，继续支持原四个参数、双角色、写入确认、实际终态和审计统计，验证后删除临时guided-legacy页面和入口。区分文件复用、行去重、对账重复、风险疑似重复。
- [x] 实现工程区的机制/脚本/信号/证据卡。脚本示例明确使用仓库要求的 `finguard-day6` 隔离项目、临时环境文件与恢复动作；与本次浏览器栈分开，避免共享消费者。截图或文字说明不能使未执行项变为已验证。
- [x] Node回归及静态资源集成测试通过后提交 `feat: add complete business scenarios and engineering evidence`。

### Task 12: 真实浏览器、隔离验收与覆盖交付

**Files:** 创建 `T/package.json`、`T/package-lock.json`、`T/browser-acceptance.mjs`、`T/browser-cases/business.mjs`、`T/browser-cases/scenarios.mjs`、`T/browser-cases/reliability.mjs`、`T/browser-cases/accessibility.mjs`、`T/invoke-console-acceptance.ps1`、`T/tests/acceptance-runner.test.ps1`、`docs/DEMO_COVERAGE.md`、`docs/review/2026-10-09-complete-demo-console-acceptance.md`；修改 `.gitignore`、`README.md`、`docs/API.md`、`docs/DEMO.md`、`JT/DemoStaticResourceSecurityIntegrationTest.java`。

**Interfaces:** PowerShell runner 参数 `-DryRun`、`-PreflightOnly`、`-Cases business,scenarios,reliability,accessibility`。输出是明确归属的隔离资源清单、脱敏验收摘要及证据路径；正常运行在finally清理。浏览器 cases 导出 `run({browser,baseUrl,credentials,evidenceDir}) -> Promise<CaseResults>`，结果字段 `name,status,evidence,limitation`。

- [x] 先写runner DryRun测试：项目名 `finguard-console`；端口 App=28080、MySQL=23306、RabbitMQ=25672、RabbitMQManagement=25673、Redis=26379、Prometheus=29090、Grafana=23000；容器名覆盖、临时随机凭据、命令兼容 docker compose/docker-compose、清理目标只包含自身资源；无泄露env正文；资源名称或端口冲突直接停止。
- [x] 运行 `powershell -NoProfile -File scripts/demo/tests/acceptance-runner.test.ps1`，确认缺失runner/隔离条件导致失败。
- [x] 实现runner，参考 Day7 六服务验收但使用新项目、覆盖container_name及各端口；生成临时.env和compose override；在清理前核对 Compose labels、绝对文件路径、持有的进程/资源 ID。脚本不会复用用户开发.env或固定演示密码。基准场景显式设置设计的三条规则默认值；另启动规则关闭/非默认阈值的验收变体，并分别记录实际配置来源。T依赖目录与运行产物加入gitignore，lockfile提交。
- [x] 安装仅测试Playwright及Chromium；编写实际业务浏览器用例：两角色认证，账户完整生命周期，人工交易 CRUD，任意CSV与行错误，历史列表/刷新找回，对账所有分支，三规则，两决定/409，审计与统计。预期与HTTP/API事实核对，不只断言按钮文案。
- [x] 编写可靠性/可用性用例：101条分页；第二页批次审计；双击写入只一次；模拟GET超时和晚到响应；刷新重新认证后查回任务；提交409保留备注；HTML样本仅呈现文本；键盘操作；390px与桌面截图。存储断言为空、事件记录无Token或密码。
- [x] 先对尚未满足的浏览器断言运行选定case，记录失败；修正产品实现后运行全部cases。任何阻断项不能改为skip并声称完整验收。
- [x] 运行 `mvn.cmd -B -ntp clean test` 与 `node --test scripts/demo/tests/*.test.mjs`，记录实际结果；运行runner全部分组，核对六服务、任务、指标、Grafana入口和隔离清理。
- [x] 逐项复核工程卡：对实际运行的演练记录新证据，对未运行的压力/安全/CI/部署项目标注历史复现入口与限制；不宣称新性能或部署结论。已有演练若限制项目名，创建专用day6栈并隔离浏览器栈后运行。
- [x] 覆盖表一行一个接口/原因/规则/工程项，27个操作全部有页面入口，所有浏览器已验收业务分支有证据。更新文档与总数；移除建设状态。`git diff --check`及全分支审查后提交 `test: verify complete console and document coverage`。

## 关键断言样例

以下为任务测试必须包含的关键断言形式；变量由对应步骤的实际响应或请求捕获赋值，测试不得从预期常量伪造实际值。额外行为按任务中的命名与断言描述覆盖。

```java
// Task 1：Environment 的连接值与真实容器值一致。
assertThat(environment.getProperty("spring.rabbitmq.port"))
    .isEqualTo(FinGuardTestContainers.springProperties().get("spring.rabbitmq.port").toString());
// Task 2：真实权限；用现有jwt() helper分别赋予角色。
mockMvc.perform(get("/api/review-tasks/1/context").with(adminJwt))
    .andExpect(status().isForbidden());
// Task 3：空列表元数据与非法size由HTTP验证。
mockMvc.perform(get("/api/import-jobs").with(reviewerJwt).param("size", "101"))
    .andExpect(status().isBadRequest());
assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds);
assertThat(allPageIds).hasSize(101);
```

```javascript
// Task 4：网络失败没有隐式重放；销毁后晚响应没有渲染。
assert.equal(capturedWriteCalls.length, 1);
assert.equal(renderedTaskId, selectedTaskId);
// Task 5：取消破坏性确认后，不提交删除。
assert.equal(capturedCalls.filter(c => c.method === 'DELETE').length, 0);
// Task 6：捕获实际请求里的精确金额字符串。
assert.equal(capturedTransactionBody.amount, '0.10');
// Task 7：主动重复验证必须复用同一原始文件字节。
assert.deepEqual(repeatedUploadBytes, originalUploadBytes);
assert.equal(repeatedResponse.data.id, originalResponse.data.id);
// Task 8：结果服务端筛选只采用现有契约。
assert.equal(resultsQuery.get('resultType'), 'DUPLICATE');
assert.equal(resultsQuery.get('page'), '2');
// Task 9：409没有重发，且没有丢失备注。
assert.equal(capturedDecisionCalls.length, 1);
assert.equal(noteInput.value, originalNote);
// Task 10：真实第二页匹配日志被包含。
assert.ok(displayedAuditIds.includes(secondPageMatchingId));
// Task 11：规则实际无命中，不生成前端风险事实。
assert.deepEqual(displayedRiskHits, serverRiskHits);
// Task 12：真实浏览器会话存储为空，桌面和390px截图另外保存。
assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
```

## 自检与执行交接

设计第1/5/6节由 Task 2–10 覆盖；第3/4/8节由 Task 4 与12覆盖；第7节及原演示由 Task11/12覆盖；工程与证据由 Task11/12覆盖。前端共用接口只在本计划契约处定义，后续使用相同签名；每项Review Focus均有所属任务与失败断言。

推荐当前会话直接执行：共用会话与UI契约贯穿多个工作区，按顺序实施可以减少重复理解成本；最后做一次独立全分支审查。另一方式是逐任务由实现子代理与审查子代理交替执行，每步有独立审查但上下文成本更高。

用户审阅计划并选择方式后开始 Task1。根据所选方式使用对应执行技能，保留按任务的进度记录，持续推进到整个计划验收结束，不在任务之间重复询问是否继续。
