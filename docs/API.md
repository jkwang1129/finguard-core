# FinGuard Core API 参考

## 1. 入口与认证

- OpenAPI JSON：`GET /v3/api-docs`
- Swagger UI：`GET /swagger-ui/index.html`
- 登录：`POST /api/auth/login`
- 业务请求：`Authorization: Bearer <JWT>`

JWT 有效期为两小时。`ADMIN` 可执行业务写入并读取审计；`REVIEWER` 可读业务并独占审核决策权限。匿名业务请求返回 401，已认证但角色不足返回 403。

## 2. 20 条业务路径、27 个业务操作

| 方法 | 路径 | ADMIN | REVIEWER | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/auth/me` | 已认证 | 已认证 | 查询服务端身份 |
| POST | `/api/auth/login` | 公开 | 公开 | 校验凭据并签发 JWT |
| POST | `/api/accounts` | 允许 | 403 | 创建账户 |
| GET | `/api/accounts` | 允许 | 允许 | 筛选和分页账户 |
| GET | `/api/accounts/{id}` | 允许 | 允许 | 查询账户详情 |
| PATCH | `/api/accounts/{id}/name` | 允许 | 403 | 修改名称 |
| PATCH | `/api/accounts/{id}/status` | 允许 | 403 | 启用/停用 |
| DELETE | `/api/accounts/{id}` | 允许 | 403 | 软删除未使用账户 |
| POST | `/api/transactions` | 允许 | 403 | 创建 MANUAL 交易 |
| GET | `/api/transactions` | 允许 | 允许 | 筛选和分页交易 |
| GET | `/api/transactions/{id}` | 允许 | 允许 | 查询交易详情 |
| PUT | `/api/transactions/{id}` | 允许 | 403 | 替换 MANUAL 交易 |
| DELETE | `/api/transactions/{id}` | 允许 | 403 | 软删除 MANUAL 交易 |
| GET | `/api/import-jobs` | 允许 | 允许 | 历史分页；status、createdBy 筛选 |
| POST | `/api/import-jobs` | 允许 | 403 | multipart 上传 CSV；首次 202，重复文件 200 |
| GET | `/api/import-jobs/{id}` | 允许 | 允许 | 查询异步导入状态 |
| GET | `/api/import-jobs/{id}/errors` | 允许 | 允许 | 查询行错误 |
| GET | `/api/reconciliation-jobs` | 允许 | 允许 | 历史分页；status、importJobId、createdBy 筛选 |
| POST | `/api/reconciliation-jobs` | 允许 | 403 | 首次受理 202，重复请求 200 |
| GET | `/api/reconciliation-jobs/{id}` | 允许 | 允许 | 查询对账状态 |
| GET | `/api/reconciliation-jobs/{id}/results` | 允许 | 允许 | 查询逐笔结果 |
| GET | `/api/review-tasks` | 允许 | 允许 | 筛选审核任务 |
| GET | `/api/review-tasks/{id}` | 允许 | 允许 | 查询审核任务详情 |
| GET | `/api/review-tasks/{id}/context` | 403 | 允许 | 脱敏审核上下文 |
| PATCH | `/api/review-tasks/{id}/decision` | 403 | 允许 | 携带 version 确认或忽略 |
| GET | `/api/audit-logs` | 允许 | 403 | 查询业务审计 |
| GET | `/api/statistics/overview` | 允许 | 允许 | 查询缓存统计概览 |

OpenAPI 的路径对象为 20 个；同一路径可能包含多个 HTTP 方法，因此上表操作数为 27。

## 3. 异步契约

上传和对账创建只表示“已受理”，不表示后台处理完成。客户端应读取 `Location` 或响应 ID，轮询到终态：

- 导入：`SUCCESS | PARTIAL_SUCCESS | FAILED`；
- 对账：`COMPLETED | FAILED`。

重复文件和重复对账请求返回原任务并标记重复，不创建第二份业务事实。客户端不得用固定等待时间替代终态轮询。

## 4. 分页与筛选

列表响应统一包含当前页内容和分页元数据。页号、页大小、枚举、时间范围等由 Jakarta Validation 和领域规则共同校验；默认排序同时使用业务时间与 ID，避免时间相同时翻页漂移。具体可选参数以运行时 OpenAPI 为准。

## 5. 错误响应

统一结构包含 `timestamp`、`status`、`code`、`message`、`path` 和 `fieldErrors`。常用语义：

| HTTP | 典型 code | 含义 |
| ---: | --- | --- |
| 400 | `VALIDATION_FAILED`, `INVALID_REQUEST`, `INVALID_IMPORT_FILE` | 请求格式或业务输入非法 |
| 401 | `INVALID_CREDENTIALS`, `AUTHENTICATION_REQUIRED`, `INVALID_TOKEN` | 登录失败或身份未建立 |
| 403 | `ACCESS_DENIED` | 身份有效但角色不足 |
| 404 | `*_NOT_FOUND`, `RESOURCE_NOT_FOUND` | 资源不存在或路由不存在 |
| 409 | `DUPLICATE_*`, `INVALID_*_OPERATION`, `REVIEW_VERSION_CONFLICT` | 唯一性、状态或并发冲突 |
| 413 | `INVALID_IMPORT_FILE` | 文件超过限制 |
| 429 | `RATE_LIMIT_EXCEEDED` | 固定窗口超限；同时返回 `Retry-After` |
| 500 | `INTERNAL_SERVER_ERROR` | 未分类服务端错误，响应不暴露堆栈 |

## 6. 最小演示顺序

登录 ADMIN → 创建账户与 MANUAL 交易 → 上传 `sample-data/demo-import.csv` → 轮询导入 → 创建并轮询对账 → 登录 REVIEWER 决策风险任务 → ADMIN 查询审计与统计。完整命令和清理步骤见[演示手册](DEMO.md)。
