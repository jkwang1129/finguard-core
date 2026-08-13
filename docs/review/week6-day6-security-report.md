# Week 6 Day 6 安全验证报告

## 1. 结论

Day 6 对认证、授权、输入/CSV、SQL/持久化、秘密、容器/部署、Actuator/Redis 和 RabbitMQ 八类边界进行了源码、自动化、真实 HTTP、仓库/镜像扫描和被动 Web 基线验证。

- 确认 1 个 Low 配置缺陷：开发 Compose 的 MySQL 端口原先绑定所有宿主机网卡（CWE-668）。已改为 `127.0.0.1` 并加入回归断言。
- 没有确认可复现的 Critical 或 High 应用漏洞。
- Trivy 仓库扫描为 0 漏洞、0 配置错误、0 秘密；镜像 OS 扫描报告 299 个供应链条目（11 High、133 Medium、155 Low），经可达性和厂商状态核验后没有形成当前 HTTP 服务可利用的 High 漏洞，但基础镜像必须持续重建和复扫。
- 真实 HTTP 负向矩阵和 31 个认证/RBAC/CSV 聚焦测试全部通过。
- 标准源码审计在目标提交 `5f424e1abb3cde08baca2f7afbc59a660302b4af` 上完成，7 个安全面关闭，报告 1 个 Low；该问题已在当前工作树修复。

## 2. 范围与威胁模型

### 保护资产

- 用户凭据和 JWT 签名材料。
- 账户、交易、导入、对账、风险、审核与审计事实。
- MySQL、RabbitMQ、Redis、Prometheus、Grafana 状态。
- 运行时秘密与不可变应用镜像。

### 攻击者与边界

- 匿名或持有普通/复核员凭据的 HTTP 客户端。
- 能接触开发主机已发布端口的同网段攻击者。
- 不可信的 JSON、查询参数和 CSV 文件。
- 应用到 MySQL/RabbitMQ/Redis 的依赖边界，以及宿主机到 Docker 已发布端口的边界。

生产假设仍是 `compose.linux.yml`：依赖不发布宿主机端口，应用与监控只绑定回环地址，并通过 SSH 或同等受控通道访问。主动渗透 ECS 和公网目标不在 Day 6 范围内。

## 3. 工具与目标

| 项目 | 版本/目标 |
| --- | --- |
| 源码标准审计 | Codex Security，目标 SHA `5f424e1abb3cde08baca2f7afbc59a660302b4af` |
| Trivy | `aquasec/trivy:0.72.0`，digest `sha256:cffe3f5161a47a6823fbd23d985795b3ed72a4c806da4c4df16266c02accdd6f` |
| 仓库扫描 | `vuln,misconfig,secret`，离线模式；排除 `.git`、`target`、扫描缓存/结果和 Day 5 生成型性能工具/结果 |
| 镜像扫描 | `finguard-core:day5-local`，image ID `sha256:5036395e99d5e5a5924baa22a7d0105f8ae75237de3388efd2973f87537d2410` |
| Web 基线 | OWASP ZAP Baseline `ghcr.io/zaproxy/zaproxy:2.17.0`，digest `sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef`，仅本机隔离应用 |
| Java | Java 17.0.10（Maven 测试）；镜像运行时 Temurin 17.0.19 |

原始 JSON/HTML/日志和漏洞数据库保存在 Git 忽略的 `security/results/`、`security/.cache/`，不提交仓库。

## 4. 认证、授权和敏感端点

### 源码控制

- Spring Security 使用无状态 Bearer JWT，禁用服务端 Session；CSRF 禁用与无 Cookie 认证模型一致。
- 匿名只允许登录、健康/Prometheus 和 OpenAPI/Swagger 路由；业务默认必须认证。
- ADMIN 可写业务并读审计，REVIEWER 只读业务且只能执行审核决策；审计日志仅 ADMIN。
- JWT 固定 HS256，Base64 解码后密钥至少 32 字节，同时验证 issuer、签发时间与过期时间。
- 登录请求的 `toString()` 固定脱敏密码。

### 真实 HTTP 结果

| 场景 | HTTP |
| --- | ---: |
| 匿名读取账户 | 401 |
| 非法 Bearer Token | 401 |
| 已正确签名但过期的 JWT | 401 |
| 非法登录 JSON | 400 |
| ADMIN 登录 | 200 |
| REVIEWER 登录 | 200 |
| REVIEWER 读取账户 | 200 |
| REVIEWER 创建账户 | 403 |
| ADMIN 读取审计 | 200 |
| REVIEWER 读取审计 | 403 |
| 匿名读取 `/actuator/env` | 401 |
| ADMIN 读取未暴露的 `/actuator/env` | 404 |

认证、RBAC、HTTP 上传与 CSV 解析聚焦测试 31/31 通过，0 失败、0 错误、0 跳过。

## 5. 输入、文件、SQL 与秘密

- Multipart 上限为 5 MiB；CSV 使用严格 UTF-8、RFC 4180 结构、固定字段契约和 10,000 行上限。
- 文件名只作为受控元数据，不作为服务端路径；原始文件由数据库持久化，不能形成目录穿越写入。
- DTO 使用 Jakarta Validation；金额、枚举、时间和分页参数另有领域约束。
- MyBatis Mapper 使用 `#{...}` 参数绑定；未发现由请求值进入 `${...}` 动态 SQL 的路径。
- 幂等唯一键、条件更新、事务和行锁保护重复消息与并发写入。
- `.env`、扫描结果、扫描缓存和临时 Token/环境文件均被 Git 忽略；真实秘密仅运行时注入。

## 6. 确认并修复的 Low 问题

### 开发 MySQL 发布到所有宿主机网卡（CWE-668）

目标提交中的配置为：

```yaml
- "${MYSQL_PORT:-3306}:3306"
```

Docker 会把未指定宿主机 IP 的发布端口绑定到所有接口。攻击者仍需网络可达且通过 MySQL 认证，因此严重度为 Low，但这扩大了开发数据库的攻击面，并与仓库内其他回环绑定以及 Linux 私有依赖拓扑不一致。

修复为：

```yaml
- "127.0.0.1:${MYSQL_PORT:-3306}:3306"
```

验证包括 PowerShell 合约测试、`docker compose config --quiet` 和隔离栈实际发布地址检查。没有修改生产数据、Flyway 或业务语义。

## 7. 镜像扫描告警验证

### 扫描汇总

| 结果 | High | Medium | Low | 配置 | 秘密 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 仓库/POM | 0 | 0 | 0 | 0 | 0 |
| UBI 9.8 OS 包 | 11 | 133 | 155 | 0 | 0 |

11 个 High 实际是 6 个唯一 CVE：5 个 curl/libcurl 条目因两个 RPM 重复计数，另 1 个为 libpng/OpenJDK 元数据条目。Trivy 均未提供 fixed version。

### curl/libcurl 五个唯一 CVE

- `CVE-2026-11352`：要求应用作为 HTTP/3 curl 客户端连接恶意服务端。
- `CVE-2026-11586`：要求 curl WebSocket 客户端接收恶意 PING 洪水。
- `CVE-2026-8286`：要求 curl 使用 STARTTLS 且复用 TLS 配置不匹配的连接。
- `CVE-2026-8925`：要求 curl 的 SASL/GSASL 路径；Red Hat 数据将 RHEL 9 标为 Not affected，但 Trivy 仍标为 affected。
- `CVE-2026-9547`：要求 libcurl SCP/SFTP 和 `CURLOPT_SSH_KEYFUNCTION`。

FinGuard 请求处理和依赖客户端均由 Java/Spring 驱动，不执行 `/usr/bin/curl`，也没有 HTTP/3 curl、WebSocket curl、STARTTLS、SASL curl 或 SCP/SFTP 回调路径。镜像健康检查使用 `wget`。因此这些是存在于基础层但当前应用路径不可达的供应链告警，不作为 FinGuard 可复现 High 漏洞关闭；基础镜像有修复版本后仍应立即重建。

### `CVE-2026-22020`

扫描器把该条目关联到系统 `libpng`，但镜像已经使用 Temurin 17.0.19；Oracle/发行版记录显示 17.0.19 已包含对应 libpng 更新，Red Hat 跟踪又明确记录该 CVE 已被官方拒绝。该条目按数据映射误报关闭，不形成应用漏洞。

### 处置边界

- 当前 `eclipse-temurin:17-jre-ubi9-minimal` 最新拉取结果与应用镜像的 UBI 9.8 build date/release 相同，简单重建不会改变这些 RPM。
- 不为压低扫描数字而在 Day 6 擅自替换整个运行时发行版；这会扩大回归与部署范围。
- 保持固定扫描器、每日/每次 CI 重建、供应商状态核验，并在 fixed version 出现后升级基础镜像。

## 8. Redis、RabbitMQ 与可用性安全

- Redis 缓存失败回源 MySQL；登录/上传限流在 Redis 故障时 fail-open。这是有意的可用性取舍，不影响 RBAC、数据库唯一键、消息幂等或审计约束。
- 登录按 IP 摘要与规范化用户名限流，上传按 JWT user ID 限流，Lua 保证计数和 TTL 原子性。
- RabbitMQ 消费并发与 prefetch 有界，两级重试有限，耗尽后进入独立 DLQ；失败头和指标只使用固定低基数字段，不包含 JWT、密码、CSV 或堆栈。
- MySQL 是任务和幂等真源；重复投递、ACK 丢失与并发消费由条件更新、行锁和唯一约束兜底。

## 9. ZAP 被动基线告警验证

根路径基线抓取 3 个 401 URL，66 条规则通过、0 Fail、1 条“不可缓存”提示；401 是未认证访问受保护根路径的预期结果。公开 Swagger UI 基线抓取 13 个 URL，60 条规则通过、0 Fail，产生 7 组 Warning：

- DOMPurify 3.3.2：ZAP 标记为易受攻击库。已核对 bundle 的唯一调用使用 `sanitize(..., { ADD_ATTR, FORBID_TAGS, ALLOW_DATA_ATTR, FORBID_ATTR })`，没有使用 2026 年新公告所需的 `IN_PLACE`；早期影响 `<=3.3.1` 的三项 XSS 已由 3.3.2 修复。OpenAPI 描述来自编译期注解，不存在匿名或业务用户写入规范的入口，因此当前告警不可达，作为依赖升级观察项关闭。
- Swagger HTML 缺少 CSP：当前未发现可控 HTML/脚本注入源，生产仅通过回环/SSH 访问，属于防御纵深改进而非可复现漏洞。后续若将 Swagger 暴露给浏览器公网访问，应设计与 Swagger 内联资源兼容的 CSP，不能直接加入会破坏页面的策略。
- Permissions Policy、COOP/COEP/CORP：公开文档页面不使用摄像头、麦克风或跨源嵌入，属于浏览器隔离加固建议。
- Unix 时间戳来自固定第三方压缩 JS，不是 Token、业务时间或部署秘密。
- Non-Storable/Modern Web Application 为信息性提示；文档静态资源使用 `no-store` 只影响缓存效率。

ZAP 仅使用 `zap-baseline.py`，没有执行 full scan、API active scan 或任何 ECS/公网扫描。

## 10. 限制与后续动作

- Trivy 离线 POM 扫描无法解析一个由依赖管理提供的空版本属性，且镜像 Java JAR 为避免额外下载约 905 MiB 的 Java 漏洞库而由仓库扫描承担；完整 Maven 依赖解析和测试成功，但这不等同于完整的软件成分分析。CI 后续应缓存 Trivy Java DB 或接入依赖清单/SBOM 扫描。
- 标准源码审计按 7 个重点安全面执行，未逐文件审阅全部文档和所有测试；受当前任务策略约束，没有独立委派审计员，因此覆盖标记为 partial。
- ZAP Baseline 是被动、未认证基线，不能证明认证后所有业务响应不存在 Web 类问题；认证/RBAC 由真实 HTTP 和自动化测试补充。
- 扫描数据会漂移。基础镜像、Trivy 数据库和厂商 VEX/errata 应在每次发布重新核验。

## 11. 验收判断

当前没有未处置的可复现 Critical/High FinGuard 漏洞。1 个确认 Low 已修复；基础镜像 High 告警均已记录可达性和厂商状态，不被静默忽略。

干净 `finguard-day6` 隔离卷上的 Java 17 完整回归为 379/379，0 失败、0 错误、0 跳过；最终隔离应用 health 为 200。第一次完整回归因演练 Bootstrap 用户仍留在同一验收库而出现 1 失败、1 错误，重建仅属于 Day 6 的隔离卷后无需修改业务代码即全部通过，根因确认是验收环境污染而非产品回归。

最终已删除 Day 6 容器、网络、命名卷、扫描结果/缓存、临时环境文件和一次性密码文件；普通开发 MySQL、RabbitMQ、Redis 仍保持原状态。安全工具契约测试、Compose 配置解析、秘密/范围审计和 `git diff --check` 纳入提交前门禁。
