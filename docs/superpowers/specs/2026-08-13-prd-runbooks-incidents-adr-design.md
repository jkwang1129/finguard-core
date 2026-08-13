# PRD、Runbooks、Incidents 与 ADR 文档补齐设计

日期：2026-08-13（Asia/Shanghai）

## 1. 目标

补齐 FinGuard Core 最终交付目录中的产品需求、运维手册、故障复盘和架构决策记录，让首次阅读仓库的人能够分别回答：系统为什么存在、如何安全操作、发生过什么故障、关键技术选择为什么成立。

本次只重组并提炼已经实现和验证的事实，不修改 Java、数据库迁移、Compose、CI、部署脚本或业务行为。

## 2. 事实来源与边界

- 产品范围以根目录 `PROJECT_BRIEF.md`、`TASKS.md` 和当前实现为准。
- 架构、数据库、API、部署、监控、性能、安全和最终验收分别引用现有顶层文档与 `docs/review/` 证据。
- 运维命令必须来自当前脚本、Compose 文件和已验证流程；不复制真实口令、JWT、私钥、主机地址或 Registry Token。
- 故障记录只覆盖已经完成的 Redis、RabbitMQ 和 MySQL 三次隔离演练，不把演练写成真实生产事故。
- ADR 记录已经落地的关键决策，不创建新的架构范围，也不把个人项目描述为生产系统。
- 不移动或删除 `docs/design/`、`docs/review/` 中的历史文档；新文件作为稳定入口，通过相对链接指向详细证据。

## 3. 方案选择

采用“证据链接型补齐”：每个新文件独立说明自己的主题，同时将易漂移的数字、命令结果和长篇推导链接到已有源记录。

未采用以下方案：

1. 只创建目录索引：文件少，但不能独立指导读者执行或理解决策。
2. 全面迁移历史文档：目录统一，但会复制内容、破坏已有链接并降低 Git 历史可追溯性。

## 4. 文件结构与职责

```text
docs/
├── PRD.md
├── runbooks/
│   ├── README.md
│   ├── local-acceptance.md
│   ├── linux-deployment-and-rollback.md
│   └── dependency-incident-response.md
├── incidents/
│   ├── README.md
│   ├── redis-outage.md
│   ├── rabbitmq-retry-dlq.md
│   └── mysql-misconfiguration.md
└── adr/
    ├── README.md
    ├── 0001-modular-monolith.md
    ├── 0002-mysql-truth-and-outbox.md
    ├── 0003-rbac-duty-separation.md
    ├── 0004-redis-fail-open.md
    └── 0005-immutable-image-deployment.md
```

同时修改根目录 `README.md` 的文档索引，让四类入口可直接发现。

## 5. PRD 设计

`docs/PRD.md` 面向产品、面试官和新开发者，采用“需求而非实现细节”的表达，包含：

1. 文档状态、产品定位和个人项目边界；
2. 目标用户与核心问题；
3. 登录、CSV 异步导入、自动对账、风险审核、审计统计的端到端用户旅程；
4. 按模块编号的功能需求及其可观察验收条件；
5. 数据正确性、幂等性、安全、性能、可观测性、部署与恢复等非功能需求；
6. 明确的范围外内容和已知限制；
7. 成功标准与现有证据索引。

PRD 不重复列出完整字段表或所有 HTTP 请求示例；这些内容分别链接到 `DATABASE.md` 和 `API.md`。

## 6. Runbook 设计

### 6.1 索引

`docs/runbooks/README.md` 说明使用前提、手册选择、秘密处理、危险操作边界和升级路径。

### 6.2 本地全链路验收

`local-acceptance.md` 记录 Java 17、Maven、Docker/Compose 前置条件，以及 DryRun、Preflight、完整验收、结果字段解释和独立残留检查。所有破坏性清理只允许命中固定 `finguard-day7` 资源。

### 6.3 Linux 部署与回滚

`linux-deployment-and-rollback.md` 串联 preflight、完整 SHA 部署、状态/有限日志、安全停止、升级和应用镜像回滚。明确回滚保留卷，但不回滚 Flyway、业务数据或外部副作用。

### 6.4 依赖故障响应

`dependency-incident-response.md` 给出统一处置顺序：确认影响、查看健康和有限日志、区分 Redis/RabbitMQ/MySQL、执行对应恢复、验证业务与监控、保留证据。它只引用安全命令和现有演练脚本，不提供生产凭据或无界日志命令。

## 7. Incident 设计

`docs/incidents/README.md` 说明这些记录均为隔离故障演练，并提供三份记录、对应运行手册和总复盘的索引。每个故障文件采用同一模板：

- 性质与日期；
- 影响范围；
- 现象与检测；
- 时间线；
- 根因；
- 恢复步骤；
- 验证与清理；
- 预防措施；
- 证据链接。

三个文件分别覆盖 Redis 中断后的 MySQL 回源与限流 fail-open、RabbitMQ 受控消费者下的两级重试/DLQ、错误 MySQL 端口导致的快速失败与健康实例隔离。标题和正文均明确标注为“故障演练”，避免冒充生产事故。

## 8. ADR 设计

ADR 使用递增四位编号，状态统一为 `Accepted`，包含：背景、决策、理由、后果、替代方案、证据。

1. `0001-modular-monolith.md`：选择模块化单体，保留事务和学习边界，拒绝无依据微服务拆分。
2. `0002-mysql-truth-and-outbox.md`：MySQL 作为事实源，Outbox 缩小数据库与 RabbitMQ 双写窗口，消费者仍承担幂等。
3. `0003-rbac-duty-separation.md`：ADMIN 管理业务写入、REVIEWER 独占审核决策，形成职责分离。
4. `0004-redis-fail-open.md`：统计缓存可回源，限流在 Redis 不可用时 fail-open；接受频控暂时削弱并依赖健康/指标告警。
5. `0005-immutable-image-deployment.md`：镜像以完整 Git SHA 发布，部署与回滚不使用 `latest`，保留数据卷且诚实声明数据库回滚边界。

`docs/adr/README.md` 说明编号、状态和新增 ADR 规则：已接受决策不直接覆写语义；发生重大反转时新增 ADR 并标记被替代关系。

## 9. 链接与信息流

```text
README
  ├─ PRD ──> ARCHITECTURE / DATABASE / API / evidence
  ├─ runbooks ──> scripts / DEPLOYMENT / MONITORING / evidence
  ├─ incidents ──> runbook / drill scripts / fault-drill evidence
  └─ ADR ──> ARCHITECTURE / design records / acceptance evidence
```

新文档之间使用相对链接，外部 CI 仅引用已有真实运行链接。任何数字必须来自当前最终复盘或具名历史证据，不能从配置文件存在推断运行成功。

## 10. 验证标准

实施完成后必须满足：

1. 上述 15 个新文件存在且非空，README 有四类入口；
2. PRD 的功能范围与当前 API、数据库和最终验收一致；
3. 三份故障记录均明确是隔离演练，并与源复盘的现象、根因和恢复一致；
4. 五份 ADR 均包含背景、决策、后果、替代方案和证据；
5. 所有项目 Markdown 相对链接均可解析；
6. 新文件不包含私钥、JWT、密码赋值、主机地址或临时结果；
7. `git diff --check` 通过，差异只涉及本设计范围内的文档；
8. 因为不修改运行代码，实施阶段不重复执行 379 项 Maven 回归；以文档结构、链接、敏感信息和 Git 范围检查作为比例适当的验证。

## 11. 失败处理与回滚

- 如果发现新文档与当前实现冲突，以源码、迁移、测试和最新验收证据为准，修正文档而不是扩大业务范围。
- 如果相对链接检查失败，修正链接后重新执行全量 Markdown 检查。
- 如果出现敏感信息命中，先判断是否为明确占位符；无法确认安全时删除具体值并改为环境变量名称。
- 本次变更没有数据或运行时副作用；回滚只需移除新目录/文件并恢复 README 索引，不执行 Docker、数据库或远端操作。
