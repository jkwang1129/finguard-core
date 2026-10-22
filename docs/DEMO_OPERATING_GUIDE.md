# FinGuard Core 演示应用完整操作指南

适用版本：本仓库 `main` 中的业务控制台。默认入口：<http://127.0.0.1:8080/demo/index.html#guided>。

本指南按“第一次上手 → 面试主流程 → 异常场景 → 独立管理功能 → 工程信号 → 结束与恢复”组织。每一步都说明点击什么、观察什么、怎样解释结果。

## 1. 环境准备与开始前检查

要求 Java 17、Maven 3.9+、Docker 与支持 `up --wait` 的现代 Compose。启动脚本支持插件与独立命令两种形式；旧版 Compose v1 需要升级，预检会在启动前提示。以下命令均从你克隆的仓库根目录执行，不需要额外 worktree，也不需要另建前端服务。

1. 复制 `.env.example` 为 `.env`，将 `replace-with-*` 占位值替换为自己的本地密码；`JWT_SECRET_BASE64` 使用至少 32 个随机字节的 Base64 编码。配置不上传 Git。
2. 首次需要创建演示账号时，在 `.env` 中填写：

   ```dotenv
   FINGUARD_AUTH_BOOTSTRAP_ENABLED=true
   FINGUARD_AUTH_BOOTSTRAP_ADMIN_USERNAME=demo-admin
   FINGUARD_AUTH_BOOTSTRAP_ADMIN_PASSWORD=<自行设置本地密码>
   FINGUARD_AUTH_BOOTSTRAP_REVIEWER_USERNAME=demo-reviewer
   FINGUARD_AUTH_BOOTSTRAP_REVIEWER_PASSWORD=<自行设置另一个本地密码>
   ```

   使用既有账号时可以保持 Bootstrap 关闭。配置项也可通过当前进程环境变量传入；进程变量优先于 `.env`。脚本不会创建固定密码或输出凭据。
3. 设置 `JAVA_HOME` 指向 Java 17，将 Maven 放入 `PATH`，或设置 `MAVEN_HOME`；启动 Docker。
4. Windows PowerShell 执行预检与启动：

   ```powershell
   powershell -NoProfile -File scripts/demo/start-demo.ps1 -DryRun
   powershell -NoProfile -File scripts/demo/start-demo.ps1
   ```

   脚本选择可用的 `docker compose` 或 `docker-compose`，启动 MySQL/RabbitMQ/Redis 后运行本仓库应用；可用 `-DepsOnly` 仅启动依赖，或 `-SkipDependencies` 复用已有依赖。已有应用占用 8080 时先核对进程；要另用端口，可设置 `$env:SERVER_PORT='8081'` 并相应调整访问地址。

应用就绪后检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

返回 `status=UP` 后打开页面。如果显示 `ERR_CONNECTION_REFUSED`，先看第 10 节，不要反复尝试登录。

默认应用端口 8080，MySQL/RabbitMQ/Redis 端口和密码以你的 `.env` 为准。此启动方式不启动 Prometheus/Grafana；需要监控界面时按 [README](../README.md) 启动完整六服务。应用 `/actuator/prometheus` 可达不代表独立监控服务已运行。页面的写入会进入实际数据库，刷新页面不会清除数据。

## 2. 登录、角色和页面导航

### 2.1 先登录两个会话

1. 展开 `ADMIN 登录`，用本次本地演示账号 `demo-admin` 登录。
2. 展开 `REVIEWER 登录`，用 `demo-reviewer` 登录。
3. 密码使用你在本地配置的演示密码；本指南不保存密码或 Token。自定义了用户名时使用实际用户名。
4. 两个区域都应显示服务端确认的用户名和角色。
5. 将顶部 `当前操作角色` 设为 `ADMIN`。
6. 勾选 `我已了解演示会写入当前数据库，数据不会自动清理`。
7. 最后再进入 `逐步演示`，生成要执行的场景。

两个登录区域分别保留会话；顶部下拉框决定普通工作区使用哪个会话。切换角色不会赋予账号新权限。

| 权限 | ADMIN | REVIEWER |
| --- | --- | --- |
| 读取账户、交易、导入、对账、审核列表与详情、统计 | 可以 | 可以 |
| 创建和修改账户、人工交易；上传 CSV；创建对账 | 可以 | 不可以 |
| 读取审核业务上下文并提交决定 | 不可以 | 可以 |
| 读取审计日志 | 可以 | 不可以 |

`逐步演示` 内部按步骤指定角色，例如创建用 ADMIN，决定用 REVIEWER。因此执行配方时保持当前角色为 ADMIN，两个会话都预先登录；不需要为每一步切换顶部角色。

### 2.2 八个入口分别做什么

| 导航 | 用途 |
| --- | --- |
| 逐步演示 | 自动准备唯一批次，逐次执行真实 API 场景 |
| 账户管理 | 新建、查找、改名、启停、删除账户 |
| 交易管理 | 新建和维护人工交易，查看 CSV 导入交易 |
| CSV 导入 | 上传自备文件、看任务历史、终态和逐行错误 |
| 对账调查 | 主动创建对账，查看历史、逐笔结果和原因 |
| 人工审核 | 查看上下文，以 REVIEWER 确认或忽略待审核任务 |
| 审计与统计 | 追踪本批次动作，查看全数据库统计 |
| 工程与证据 | 采集应用健康和指标，打开监控及历史工程资料 |

### 2.3 必须先知道的会话限制

- 刷新页面会丢失登录、场景进度、当前文件以及内存中的操作记录；数据库记录仍保留。
- 切换导航、切换顶部角色、重新登录或注销会重新挂载工作区；逐步演示会重新生成默认场景，不能靠返回按钮恢复上次步骤。
- 点击 `生成并预览场景` 也会新建一份计划。它不删除旧数据。
- 一套配方应在同一个逐步演示工作区内执行到底。离开前记录 ID，并点击 `导出脱敏步骤证据`。
- 按钮执行中等待结果。接口受理与后台完成是两个阶段，不要用重复 POST 代替查询。
- 一次写请求报错且结果不确定时，先按历史或 ID 核对。配方会阻止隐式重放该写步骤。

## 3. 第一次完整业务演示：GUIDED + 人工审核

建议第一次照此执行，约 10–15 分钟。`GUIDED` 会创建交易、导入、对账、加载审核事实并读取审计统计；它本身没有提交审核决定，决定在配方结束后手动完成。

### 3.1 开场说明与生成预览

可以先说：

> 这个项目把内部交易和外部 CSV 账单核对，自动标出异常和风险，再交给审核人员处理，关键动作可以通过审计追踪。下面用两笔交易走一遍真实流程。

在 `逐步演示` 中填写：

| 字段 | 第一次使用的值 |
| --- | --- |
| 业务场景 | `GUIDED` |
| 人工金额 | `100.00` |
| CSV 匹配金额 | `100.00` |
| CSV 时间偏移（天） | `0` |
| 风险行金额 | `12000.00` |

点击 `生成并预览场景`，展开 `预览原始 CSV`。检查 `写入预览`：

- `account` 是新的 `DEMO-...` 账号，用来区分本次批次。
- `manualTransactions` 有一笔 100 元人工交易。
- CSV 有一笔用于匹配的 100 元交易和一笔 12000 元交易。
- `steps` 列出全部操作。此时只是预览，还没创建业务数据。
- 场景时间自动取上海业务时间的约十天前，避免时间偏移导致未来时间校验失败。

### 3.2 连续执行九步，中途不切换导航或角色

每次只点一次 `执行下一步`，等结果返回后再继续。`实际步骤证据` 主要显示最近一步；导出的证据文件保留该计划已完成的全部步骤。

| 次序 | 步骤 | 观察结果 | 现场解释 |
| --- | --- | --- | --- |
| 1 | 创建隔离演示账户 | `taskIds.accountId`、实际账户字段 | 本次有一个唯一演示账号；“隔离”指账号唯一，仍使用同一个本地数据库 |
| 2 | 创建人工交易 1 | 人工交易 ID、金额、方向、流水号和时间 | 这是内部记录，对账时与外部账单比较 |
| 3 | 上传 CSV | HTTP 202、`taskIds.importJobId` | 文件已受理，后台异步导入；202 不表示导入完成 |
| 4 | GET 导入终态 | `SUCCESS`、`totalRows=2`、`successRows=2`、错误与重复行数为 0 | 等 GET 查询到终态，才确认入库结果 |
| 5 | 创建对账 | HTTP 202、`taskIds.reconciliationJobId` | 导入完成后要主动创建对账；导入不会自动发起对账 |
| 6 | GET 对账终态 | `COMPLETED` 及匹配/未匹配等计数 | 后台对账已完成，可进一步查看逐笔原因 |
| 7 | 加载逐笔结果与审核/风险事实 | `actual.results`、`reviewTasks`、`contexts`、`riskRuleCodes` | 展示实际对账和风控事实，记录审核任务 ID |
| 8 | 主动重复创建对账验证 | 对账 ID 不变，`duplicateRequest=true` | 同一个导入任务不会再创建第二份对账 |
| 9 | 读取完整批次审计与全库统计 | `batchAudit`、`overview` | 本批次关键动作可追踪；统计是全库范围 |

在默认配置下，第 7 步通常看到：

- 100 元行：`MATCHED`，`reasonCode=EXACT_MATCH`，关联人工交易 ID。
- 12000 元行：没有人工匹配候选，因此是 `UNMATCHED` / `NO_CANDIDATE`。
- 12000 元行同时命中 `LARGE_AMOUNT`，形成风险审核任务。
- 同一 CSV 交易可能同时有“对账异常审核”和“风险命中审核”。不要把它们当成同一个任务，也不要固定断言只有一个审核任务。

大额规则默认启用，阈值 `10000.00`。若服务器修改阈值或关闭规则，以实际返回为准。对账类型和风险规则是不同维度：匹配成功的交易也可能命中风险。

第 7 步先记下 `sourceType=RISK_HIT` 且 `ruleCode=LARGE_AMOUNT` 对应的审核 ID。第 9 步完成后记录全部 ID，再点 `导出脱敏步骤证据`。

| 需要保存 | 为什么 |
| --- | --- |
| DEMO 账号、`accountId` | 后续筛选账户与交易 |
| `importJobId` | 找回导入任务及行错误 |
| `reconciliationJobId` | 找回对账和逐笔结果 |
| 大额风险审核 ID、其他 `reviewTaskIds` | 找回审核及关联审计 |

导出文件只包含允许的步骤、状态、计数、时间与 ID；不等于 CSV 原文、全部 API 响应或完整审核记录的备份。

### 3.3 配方结束后，提交人工审核

1. 切换顶部角色为 `REVIEWER`。
2. 点击 `人工审核`。
3. 在 `按 ID 找回` 中输入刚才的大额风险审核 ID，点击 `查询`。
4. 检查详情的 `sourceType`、`ruleCode`、`status=PENDING`、`version=0`，以及交易上下文。
5. 在 `审核备注` 填入例如“面试演示：已核对交易上下文，确认大额风险”。
6. 点击 `确认审核`。
7. 核对 `status=CONFIRMED`、`version=1`、审核人、审核时间和备注。

如果业务判断应忽略风险，在另一份新的待审核任务上点击 `忽略审核`，观察 `IGNORED`。`CONFIRMED` 表示确认该审核事项，不表示资金已支付，也不会把原对账结果改成 `MATCHED`。

可以讲：

> 审核身份从已验证的 JWT 获取。数据库要求任务仍是 PENDING 且版本一致才更新，决定与审计在同一事务内完成。

如果想把本批次所有待审核项处理完，对每个 `reviewTaskId` 分别查看来源和上下文，再独立决定。一个任务的决定不会自动结束其他任务。

### 3.4 重新读取审核后的审计，闭合主流程

1. 切换顶部角色为 `ADMIN`，进入 `审计与统计`。
2. 在 `完整批次审计` 填入刚才的导入 ID、对账 ID、审核 ID；多个审核 ID 用英文逗号分隔。
3. 点击 `遍历完整审计`，等到“完整遍历完成”。
4. 核对 `CSV_UPLOAD_ACCEPTED`、`RECONCILIATION_COMPLETED`、`REVIEW_CONFIRMED` 或 `REVIEW_IGNORED`。
5. 点击对应行的 `安全字段详情`，或通过 `导入`、`对账`、`审核` 跳转回业务记录。
6. 点击 `刷新全库统计`，看待审核与终态计数，并核对 `generatedAt`、`observedAt`。

GUIDED 最后一步是在人工审核之前读取的快照，所以审核后需要再次查审计。完整批次审计会遍历审计所有页，再按输入的 ID 关联；取消或中途离开表示未完整加载。并发写入时它不是数据库快照。

业务审计覆盖导入受理/失败、对账完成和审核决定；账户及交易 CRUD 不产生这一类审计事件。统计来自全数据库且可能有缓存，不能把全库计数变化全部归因于本次批次。

## 4. 面试中的异常演示：选两三项即可

每个新配方都按相同步骤操作：保持两个会话登录 → 当前角色 ADMIN → 进入逐步演示 → 选择场景 → 生成并预览 → 连续执行至最后一步 → 保存 ID / 导出 → 再离开。

预计应当拒绝的 401、403、409 是配方观察目标。页面出现对应错误色或错误码不一定代表演示失败；同时检查步骤是否按预期完成。

### 4.1 完全相同文件去重：FILE_DUPLICATE

1. 选择 `FILE_DUPLICATE`，生成场景。
2. 执行到导入终态，记下导入任务 ID。
3. 执行 `主动重复完全相同文件`。
4. 观察 HTTP 200、同一任务 ID、`duplicateFile=true`。
5. 继续执行剩余步骤并导出。

讲解：相同原始字节的文件按 SHA-256 复用任务。这个演示证明文件去重；消费者重复消息幂等需要另看消费者实现和集成测试。

### 4.2 金额不一致：AMOUNT_MISMATCH

1. 选择 `AMOUNT_MISMATCH`，预览内部金额和 CSV 金额。
2. 执行到加载逐笔事实。
3. 观察 `resultType=SUSPICIOUS`、`reasonCode=AMOUNT_MISMATCH`。
4. 查看对应对账异常审核任务。

讲解：找到相同强键但金额不一致时，不静默当成匹配。此配方自动安排不一致金额；调参输入不是所有配方的通用覆盖开关。

### 4.3 权限拒绝：REVIEWER_WRITE_403 / ADMIN_DECISION_403

选择配方，执行到 `发送实际权限验证请求`。

- `REVIEWER_WRITE_403`：REVIEWER 创建账户被后端返回 403。
- `ADMIN_DECISION_403`：ADMIN 提交审核决定被后端返回 403。
- `ANONYMOUS_401`：不带会话读取账户被返回 401。

不用手动注销或切角色，配方会指定请求身份。普通工作区会隐藏或阻止不允许的操作；仅凭按钮看不到不能证明后端权限，配方发送的是实际请求。

### 4.4 版本冲突与终态保护分别演示

- `VERSION_CONFLICT`：在任务仍为 PENDING 时主动提交不一致版本，观察 `409 / REVIEW_VERSION_CONFLICT`；随后用正确版本提交决定。它证明版本校验，不是两个真实审核人并发的复现。
- `TERMINAL_DECISION`：先正常决定，再对终态任务提交决定，观察后端实际 409 和错误码。后端先判断终态，不应把所有 409 都称为版本冲突。
- 人工审核里可用 `保存当前版本（仅 DEMO 任务）` → 正常决定 → `主动提交旧版验证`。这也可能先被终态保护拒绝。
- 收到非预期 409 后，点击 `重新加载当前版本`，看任务状态和实际错误码再决定下一步。

## 5. 所有 29 个场景速查

除 GUIDED 四输入外，配方会主动安排边界或错误数据。以写入预览和实际 API 事实为准。

| 场景 | 重点观察 |
| --- | --- |
| GUIDED | 默认两笔交易、匹配与大额风险、重复对账、审计统计；审核决定需手动补完 |
| IMPORT_SUCCESS | 全部合法行，导入 SUCCESS |
| IMPORT_PARTIAL | 一笔合法行加非法金额行，PARTIAL_SUCCESS 和行错误 |
| IMPORT_FILE_FAILED | 错表头，文件级 FAILED / INVALID_HEADER |
| FILE_DUPLICATE | 相同文件复用 ID / duplicateFile |
| ROW_DUPLICATE | 改文件内容但保持业务键，第二任务 duplicateRows；全为重复行时可为 FAILED |
| EXACT | 强键、金额、方向、时间一致，MATCHED / EXACT_MATCH |
| STRONG_TOLERANCE | 相同强键、时间差三天，MATCHED / TOLERANCE_MATCH |
| WEAK_TOLERANCE | 流水号不同，账户/方向/金额/时间窗口内唯一候选，MATCHED / TOLERANCE_MATCH |
| NO_CANDIDATE | UNMATCHED / NO_CANDIDATE |
| MULTIPLE_CANDIDATES | 多个人工候选，DUPLICATE / MULTIPLE_CANDIDATES |
| MANUAL_ALREADY_MATCHED | 候选已被强匹配占用，DUPLICATE / MANUAL_ALREADY_MATCHED |
| DIRECTION_MISMATCH | SUSPICIOUS / DIRECTION_MISMATCH |
| AMOUNT_MISMATCH | SUSPICIOUS / AMOUNT_MISMATCH |
| TIME_OUT_OF_RANGE | 时间差四天，SUSPICIOUS / TIME_OUT_OF_RANGE |
| LARGE_AMOUNT | 默认 12000，观察 LARGE_AMOUNT |
| LARGE_AMOUNT_BELOW | 9999.99，默认规则不命中大额 |
| LARGE_AMOUNT_BOUNDARY | 10000.00，默认阈值边界命中 |
| POSSIBLE_DUPLICATE | 相同账户/方向/金额、不同流水号、间隔 60 秒，疑似重复风险 |
| DUPLICATE_OUTSIDE_WINDOW | 间隔 301 秒，默认 300 秒窗口外不命中疑似重复 |
| FREQUENT_TRANSACTION | 10 分钟内五笔支出，高频风险 |
| FREQUENT_BELOW | 四笔支出，默认次数阈值前不命中高频 |
| CONFIRMED | 配方直接以 REVIEWER 决定为 CONFIRMED |
| IGNORED | 配方直接以 REVIEWER 决定为 IGNORED |
| VERSION_CONFLICT | PENDING 时提交不一致版本，409 / REVIEW_VERSION_CONFLICT |
| TERMINAL_DECISION | 已终态任务再次决定被拒绝 |
| ANONYMOUS_401 | 匿名实际请求被拒绝 |
| REVIEWER_WRITE_403 | REVIEWER 业务写入被拒绝 |
| ADMIN_DECISION_403 | ADMIN 审核决定被拒绝 |

风险场景的命中与未命中依赖当前服务器规则开关、金额阈值、时间窗口和次数配置。相同文件去重、业务行去重、对账候选 DUPLICATE、POSSIBLE_DUPLICATE 风险是不同机制，不混用结论。

### 5.1 用 GUIDED 的四个参数做对比

每组都重新生成唯一场景，执行至加载事实，再导出；不要边执行边改输入。

| 人工金额 | CSV 金额 | 偏移天数 | 风险金额 | 默认配置下的重点 |
| --- | --- | --- | --- | --- |
| 100.00 | 100.00 | 0 | 12000.00 | 精确匹配 + 大额风险 |
| 100.00 | 101.00 | 0 | 12000.00 | 匹配行出现 AMOUNT_MISMATCH |
| 100.00 | 100.00 | 3 | 12000.00 | 三天时间容差匹配 |
| 100.00 | 100.00 | 4 | 12000.00 | 超出三天范围 |
| 100.00 | 100.00 | 0 | 9999.99 | 风险行不命中默认大额规则，但仍可能因未匹配产生异常审核 |

前端参数不会修改服务器风险阈值。±3 天对账时间容差是当前后端固定规则。

## 6. 不用配方，自己录入文件完成一遍业务

这一段用于解释“普通后台怎样操作”，不依赖逐步演示的内存进度。

### 6.1 创建账户与人工交易

1. 当前角色 ADMIN，进入 `账户管理`。
2. 在创建表单填账号 `DEMO-MANUAL-20261012-A`、账户名称“手工闭环演示”、账户类型 `BANK`。
3. 点击 `创建账户`，记下实际账户 ID。每次演示换一个新后缀，防止账号重复。
4. 进入 `交易管理`，在创建表单填写：

| 字段 | 示例 |
| --- | --- |
| 账户 ID | 上一步实际返回的 ID |
| 外部流水号 | MANUAL-A-MATCH |
| 方向 | EXPENSE |
| 金额（十进制） | 100.00 |
| 业务时间 | 2026-10-01 10:00:00，使用日期时间控件 |
| 描述 | 内部交易记录 |

点击 `创建交易`。时间字段是 Asia/Shanghai 本地业务时间，不需要转成 UTC。

### 6.2 准备 UTF-8 CSV 并上传

保存以下内容为 UTF-8 `.csv` 文件。第一列账号必须与新建账号一致；如果更换账号或后缀，文件内也一起更换。

```csv
account_no,external_transaction_no,direction,amount,transaction_time,description
DEMO-MANUAL-20261012-A,MANUAL-A-MATCH,EXPENSE,100.00,2026-10-01 10:00:00,matching row
DEMO-MANUAL-20261012-A,MANUAL-A-RISK,EXPENSE,12000.00,2026-10-01 10:10:00,large amount row
```

进入 `CSV 导入`：

1. 选择 CSV，核对预览。最多预览 16 KiB，不代表整个文件都通过校验。
2. 点击 `上传 CSV`，记下导入任务 ID。
3. 页面查询至终态，核对 SUCCESS、2 行成功、0 行错误。
4. 如果超时，点击 `继续查询（GET）` 或按 ID 找回；不要再次上传来“催进度”。
5. 在同一工作区内可点击 `主动重复提交验证`，检查同 ID、duplicateFile=true。

表头要完全按示例，账号必须存在且可用，金额为正数、最多两位小数，方向为 INCOME/EXPENSE，时间使用合法且非未来的业务时间。描述包含逗号、换行或引号时按 RFC 4180 正确引用。

要展示部分失败，使用新流水号在新文件中添加一行 `amount=not-a-number`，观察 PARTIAL_SUCCESS 和 `导入行错误` 的行号、字段、错误码、原值、原因。非法表头属于文件级失败，不是逐行错误。

### 6.3 主动创建对账

1. 导入 SUCCESS 或 PARTIAL_SUCCESS 后点击 `发起对账`，跳到对账创建表单。
2. 核对 `成功导入任务 ID`，再点击 `创建对账`。跳转本身没有创建任务。
3. 记下对账任务 ID，等到 COMPLETED。
4. 在逐笔结果中看 `resultType`、`matchMethod`、`reasonCode`、CSV 与人工交易 ID。
5. 可按结果类型筛选，并点击 `CSV 详情` / `人工详情` 检查原记录。
6. 同一工作区点 `主动重复创建验证`，确认复用原对账任务。
7. 切 REVIEWER，按风险规则 LARGE_AMOUNT 筛选审核列表，检查上下文中的账户确属本次账号，再决定。
8. 切 ADMIN，按本次 ID 遍历审计。步骤同第 3.4 节。

## 7. 独立管理功能的操作方法

### 7.1 账户管理

- 用关键词、状态 ACTIVE/DISABLED、类型 BANK/CASH/PAYMENT_PLATFORM 查询。支持上一页、下一页及每页 20/50/100 条。
- 点击 `详情` 或按账户 ID 找回；点击 `查看账户交易` 进入已带账户条件的交易列表。
- ADMIN 可填新名称并点 `保存名称`，也可点 `启用账户` / `停用账户`。
- 删除要求账户已停用且没有交易历史；有交易历史的演示账户不能靠删除人工交易来假设满足条件。后端会最终验证。
- 要演示删除，另建一只从未发生交易的空账户，停用后再删，不使用主闭环账户。

### 7.2 交易管理

- 按账户 ID、方向、来源 MANUAL/CSV_IMPORT、外部流水号、起止时间筛选。
- 人工交易详情可改方向、金额、时间和描述，点 `保存交易`；可用另建的未进入主流程的人工交易演示删除，关联约束以服务端响应为准。
- CSV_IMPORT 交易只读，用来保留导入事实。不要把隐藏编辑按钮当作已经验证后端拒绝。
- 已完成对账是持久化结果，修改人工交易不会自动重跑原对账或重写历史结果；演示对比请使用新的批次。

### 7.3 导入和对账历史

- 可按状态、创建者 ID 查询历史；对账历史另可按导入任务 ID 查询。
- 记不住页面位置时用 `按 ID 找回`。导入 ID、对账 ID、审核 ID 不是同一种 ID。
- 导入 SUCCESS/PARTIAL_SUCCESS/FAILED 是终态；FAILED 不可发起对账。
- 对账 COMPLETED/FAILED 是终态；PENDING/PROCESSING 需要继续 GET。
- 原始文件与重复创建按钮的基准只在当前工作区内存中。离开后仍可读取历史，但按钮不会自动恢复旧文件。

### 7.4 人工审核的筛选

- 风险任务：来源选 RISK_HIT，规则选 LARGE_AMOUNT、POSSIBLE_DUPLICATE 或 FREQUENT_TRANSACTION；对账结果选“全部”。
- 对账异常：来源选 RECONCILIATION_EXCEPTION，按 UNMATCHED/DUPLICATE/SUSPICIOUS 筛选；风险规则选“全部”。
- 不同时填写结果类型和风险规则。MATCHED 不是对账异常审核筛选项。
- 先查 PENDING，再点 `详情与决定`；已 CONFIRMED/IGNORED 的任务不能正常再次决定。

## 8. 工程与证据的实际使用

进入 `工程与证据`，点击 `采集健康与指标`：

- 健康应有实际采集时间、HTTP 状态以及 UP。
- 展开 `查看实际指标（最多 150 行）`，查看 finguard、JVM、HTTP 指标。
- 指标存在说明应用输出了指标，不表示 Prometheus 已抓取或 Grafana 已展示。
- 业务事件计数器可能要执行业务后才出现；空值不自动等于故障。

当前本机 9090/3000 未运行，因此仅演示应用指标采集。完整六服务部署后，填写实际 Prometheus/Grafana 地址，点击 `设置监控链接`，再打开并核对：

1. Prometheus target 为 UP，可查询 `up{job="finguard-core"}`。
2. Grafana 看板可加载，HTTP、JVM、连接池和业务面板有实际数据。
3. 页面默认 URL 是配置示例，不表示该服务已启动。按钮不会启动 Docker。

MQ、Outbox、ACK、重试、DLQ、Redis 缓存/失效/降级、限流、压测、安全和 CI/CD 卡片是机制说明及复现入口。当前健康正常或任务终态不能单独证明这些机制。

正式演练请用独立 finguard-day6 环境、不同端口和隔离消费者，并按已有部署/演练文档执行。本次运行没有新增故障演练、性能或安全扫描证据。旧报告必须带原时间和环境，不能作为当前验证结果。

## 9. 面试时间安排与证据保存

| 时长 | 建议顺序 |
| --- | --- |
| 5 分钟 | 预先完成准备；GUIDED 导入/对账/逐笔事实 → 人工审核 → 批次审计 |
| 10–15 分钟 | 完整第 3 节 → FILE_DUPLICATE → 选一项权限或版本拒绝 |
| 20–30 分钟 | 主流程 → 部分失败/金额异常 → CRUD → 健康与指标 → 对应后端代码 |

每一步的说法用“操作 → 实际结果 → 业务含义 → 一个相关技术点”。业务成功结果只是机制讨论入口，不代替事务/故障验证。

离开逐步演示前导出 JSON。另保存本次 ID、账号、观察时间，以及审核后批次审计和关键详情的安全截图。脱敏导出不包含所有响应明细；后来手动审核也不会自动加入已经导出的配方文件。

## 10. 常见问题与恢复

| 现象 | 处理 |
| --- | --- |
| ERR_CONNECTION_REFUSED | 检查 Docker 依赖和 8080 监听；应用启动后刷新 |
| 登录 401 | 核对账号与密码；过期会话重新登录，之后按 ID 恢复 |
| 请求 403 | 核对当前角色；ADMIN 不能审核，REVIEWER 不能业务写入或读审计 |
| 写入未执行 | 勾选持久化确认；取消破坏性操作确认不会发送写请求 |
| PENDING/PROCESSING 久不结束 | 按 ID 继续 GET，检查 MQ、消费者和应用日志；不要重复创建 |
| POLL_TIMEOUT | 默认约 90 秒轮询期限；只表示未在期限内观察到终态，不表示任务 FAILED |
| REQUEST_TIMEOUT 或网络失败 | 写入结果可能不确定，先查历史；配方不会自动重复该写请求 |
| 切角色/导航后进度没了 | 当前工作区内存重建；使用保存的 ID 找回，不能从新配方续跑旧步骤 |
| 大额风险没出现 | 核对当前启动阈值/开关和实际金额；前端输入不改规则配置 |
| 审核任务比预想多 | 对账异常和风险命中可分别生成任务，逐项看来源 |
| 409 | 读实际 code，区分版本冲突、终态拒绝和其他业务冲突 |
| 429 | 看 Retry-After 后等待；默认登录限额 5 次/300 秒，上传 10 次/60 秒，实际以服务器配置为准 |
| 批次审计没有审核决定 | 填入实际已决定任务的 ID，重新完整遍历；审核前导出的数据是旧快照 |
| 统计没有立即变化 | 看 generatedAt、全库范围和缓存；默认 TTL 为 60 秒，业务成功提交后也会触发失效 |
| 监控链接打不开 | 先确认独立服务运行和端口；应用指标接口可达不代表监控服务运行 |

### 10.1 从源码或 JAR 重新启动

从本仓库根目录运行 `scripts/demo/start-demo.ps1 -SkipDependencies` 可在依赖已运行时重新启动当前源码。源码变更后不要继续使用旧 JAR；需要 JAR 时使用 Java 17 重新打包：

```powershell
mvn.cmd -B -ntp package
& "$env:JAVA_HOME/bin/java.exe" -jar target/finguard-core-0.0.1-SNAPSHOT.jar
```

前提：应用端口没有另一个进程监听，`.env` 与实际依赖一致，所用账号已存在或显式配置 Bootstrap。不要为匹配某台开发电脑而盲改 Redis 端口。完整六服务、Linux 部署和隔离验收参考 [演示手册](DEMO.md) 与 [部署手册](DEPLOYMENT.md)。

## 11. 演示结束

1. 保存 ID、脱敏步骤证据和审核后的实际结果。
2. 分别注销两个会话。关闭页面不会停止 Spring Boot 或 Docker。
3. 当前窗口启动的 Java 用 Ctrl+C 停止；后台 Java 先核对 8080 对应的 PID、可执行文件和 JAR 路径，再停止对应进程，不能按名称停止所有 Java。
4. 如果要停止本地依赖，在本仓库根目录执行 `scripts/demo/stop-demo.ps1 -StopOnly`，保留容器和数据卷；该脚本不会停止 Java 进程，也不会操作隔离验收项目。
5. 不用 `down -v` 清理演示。主业务数据有历史关联，页面也不是一键批次删除工具。
6. 下次生成新 DEMO 账号；旧任务通过历史和 ID 继续查询。需要完全可丢弃的验收时，使用已有隔离验收脚本，不清空当前数据库。

## 12. 核对依据

- [原演示手册](DEMO.md)：启动、Bootstrap、Swagger 与演示边界。
- [完整覆盖清单](DEMO_COVERAGE.md)：业务 API、场景及工程证据范围。
- [接口与权限](API.md)：认证、角色和接口契约。
- [面试笔记](INTERVIEW_NOTES.md)：事务、幂等、审核、缓存及部署的技术追问。
- [2026-10-09 历史控制台验收](review/2026-10-09-complete-demo-console-acceptance.md)：历史运行证据，非本次全量重验。

本指南按控制台源码及接口契约说明操作；带日期的验收报告仅代表对应版本、环境和时间的验证结果，使用前仍需检查自己的服务状态。
