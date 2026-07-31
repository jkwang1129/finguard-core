# Week 3 Day 5：同步 CSV 上传与持久化编排设计

## 1. 业务目标与范围

Day 5 把已经完成的导入任务持久层、文件解析器和逐行校验器连接成
第一版可演示的同步导入闭环：

```text
multipart file + ADMIN JWT
  → 请求级检查和原始字节 SHA-256
  → 文件哈希幂等
  → PENDING → PROCESSING
  → CSV 结构解析
  → 逐行校验
  → CSV_IMPORT 交易与行错误入库
  → SUCCESS / PARTIAL_SUCCESS / FAILED
```

本日提供三个接口：

```text
POST /api/import-jobs
GET  /api/import-jobs/{id}
GET  /api/import-jobs/{id}/errors?page=1&size=20
```

上传只允许 `ADMIN`；两个查询接口允许 `ADMIN` 和 `REVIEWER`。

本日不实现 RabbitMQ、Redis、自动对账、风险规则、审核、审计、任务
重跑、覆盖导入或任务删除。文件只在请求内存中存在，不写本地磁盘。

## 2. 文件准备与任务创建顺序

现有 `CsvImportFileParser.parse(name, bytes)` 同时完成请求检查、哈希和
结构解析。Day 5 必须把它最小拆为两个公开阶段：

```java
PreparedImportFile prepare(String originalFileName, byte[] originalBytes)

ParsedImportFile parse(PreparedImportFile preparedFile)
```

`PreparedImportFile` 是不可变的请求内对象，保存客户端原文件名、原始
字节的防御性副本、文件大小和 64 位小写 SHA-256。执行顺序为：

1. 校验文件名、扩展名、非空和 5 MiB 上限；
2. 对未经解码、去 BOM、换行转换或修剪的原始字节计算 SHA-256；
3. 按哈希查找已有任务；
4. 未命中时创建 `PENDING` 任务；
5. 任务创建后才严格解码 UTF-8、检查 BOM/换行、解析表头和记录。

原 `parse(name, bytes)` 保留并委托给两个新阶段，使 Day 3 的纯解析
调用和测试保持兼容。

请求级失败发生在第 4 步之前，不创建任务：

| 场景 | HTTP |
|---|---:|
| 缺少 `file` 表单项 | 400 |
| 文件名缺失或扩展名不是 `.csv` | 400 |
| 文件为 0 字节 | 400 |
| 文件超过 5 MiB | 413 |
| 读取上传字节失败 | 500 |

文件级失败发生在任务创建后，任务进入 `FAILED`，不产生交易和普通
行错误：

```text
INVALID_UTF8
INVALID_HEADER
MALFORMED_CSV
NO_DATA_ROWS
TOO_MANY_ROWS
```

## 3. 文件哈希幂等

首次请求按以下顺序执行：

```text
prepare
  → findByFileHash
  → insert PENDING
  → process
  → 201 Created
```

串行重复上传在预查阶段命中原任务，直接返回：

```text
200 OK
duplicateFile = true
```

并发重复上传可能同时预查未命中。数据库唯一约束
`uk_import_jobs_file_hash` 是最终兜底：

```text
request A: insert PENDING → success
request B: insert PENDING → duplicate key
request B: 在独立只读事务重新按哈希查询 → 返回 A 的任务
```

唯一键冲突不能通过同一个已失败事务继续查询，也不能映射为通用
`409`。重复请求不重新解析、不重新推进状态、不重复写入交易。

## 4. 数据库 V5

新增迁移：

```text
V5__link_import_jobs_to_transactions.sql
```

为 `transactions` 增加：

```text
import_job_id BIGINT NULL
```

并建立：

- `fk_transactions_import_job`：
  `transactions.import_job_id → import_jobs.id ON DELETE RESTRICT`；
- `chk_transactions_import_source`：
  `MANUAL` 必须没有任务，`CSV_IMPORT` 必须关联任务。

V5 只建立已经在 Week 1 设计中预留的任务追踪关系，不创建对账字段。
MySQL 为外键列建立所需索引；当前没有“按任务查询交易”的公开接口，
因此不额外添加重复索引。

Java `Transaction` 增加 `importJobId`。人工交易创建逻辑保持该字段为
`null`；导入编排固定写入当前任务 ID。

## 5. 状态机与时间

只允许：

```text
PENDING → PROCESSING → SUCCESS
                     ↘ PARTIAL_SUCCESS
                     ↘ FAILED
```

状态更新 SQL 必须同时带当前状态条件，例如：

```sql
UPDATE import_jobs
SET status = 'PROCESSING', started_at = ?
WHERE id = ? AND status = 'PENDING'
```

受影响行数不是 1 代表并发或非法状态，必须失败，不能静默覆盖。

终态定义：

| 状态 | 条件 |
|---|---|
| `SUCCESS` | `successRows > 0` 且 `failedRows = 0` |
| `PARTIAL_SUCCESS` | `successRows > 0` 且 `failedRows > 0` |
| `FAILED` | 文件级失败、系统级失败或 `successRows = 0` |

完成逐行校验后必须满足：

```text
totalRows = successRows + failedRows
duplicateRows <= failedRows
```

`startedAt` 在进入 `PROCESSING` 时写入，`finishedAt` 在进入终态时写入。
使用项目现有 `Asia/Shanghai` 业务 `Clock`，测试中注入固定时钟。

## 6. 事务边界与失败恢复

为了让 Spring 事务代理真实生效，使用两个 Service：

- 编排 Service：读取请求、调用各阶段、捕获系统失败；
- 事务 Service：创建任务、推进状态、处理并完成、独立失败恢复。

事务边界：

```text
TX-1 REQUIRES_NEW: 创建并提交 PENDING
TX-2 REQUIRES_NEW: PENDING → PROCESSING
TX-3 REQUIRED:     解析、校验、交易、行错误、统计、终态
TX-4 REQUIRES_NEW: 系统异常后 PROCESSING → FAILED/PROCESSING_FAILED
```

文件级解析异常在 TX-3 内转换为正常 `FAILED` 终态并提交。未预期的
数据库或运行时异常从 TX-3 抛出，使本次交易、行错误和终态整体回滚；
编排层随后调用 TX-4。这样不会出现任务显示成功但交易只写入一部分。

`TX-4` 只允许更新仍处于 `PROCESSING` 的任务。错误摘要使用固定安全
消息，不保存 SQL、路径、堆栈或第三方异常文本。

## 7. 批量写入与交易唯一键竞态

验证阶段已经批量预查数据库重复，但预查和写入之间仍可能出现并发
交易。数据库唯一约束
`uk_transactions_account_source_external_no` 是最终保证。

交易和行错误每 500 条一批。交易批次处理：

1. 创建事务保存点；
2. 执行一条多值 `INSERT`；
3. 成功则释放保存点；
4. 若命中交易业务唯一键，回滚到保存点；
5. 对该批次逐行重放，每行使用独立保存点；
6. 只把确认命中该业务唯一键的行转为
   `DUPLICATE_TRANSACTION`；
7. 其他完整性或系统异常继续抛出，使 TX-3 整体回滚。

禁止 `INSERT IGNORE`，因为它可能把截断、非法值或其他约束错误也降级
为警告。降级产生的新重复错误与 Day 4 已有错误一起写入
`import_row_errors`。

统计按最终结果计算：

- `successRows`：真正插入的新交易数；
- `failedRows`：最终错误集合中不同逻辑记录号数量；
- `duplicateRows`：含文件内或数据库重复错误的不同记录号数量；
- 同一逻辑记录多个字段错误只算一条失败记录。

## 8. HTTP 模型与当前用户

上传请求：

```text
Content-Type: multipart/form-data
part name: file
```

上传响应和详情响应包含：

```text
id
originalFileName
fileHash
fileSizeBytes
status
totalRows
successRows
failedRows
duplicateRows
fileErrorCode
errorSummary
createdBy
startedAt
finishedAt
createdAt
updatedAt
duplicateFile
```

首次创建返回 `201 Created` 和
`Location: /api/import-jobs/{id}`；重复文件返回 `200 OK`。
详情查询中的 `duplicateFile` 固定为 `false`，它只表达当前 HTTP 请求
是否命中了文件幂等，不是任务持久化字段。

行错误分页返回现有 `PageResponse<ImportRowErrorResponse>`，默认
`page=1,size=20`，最大 `size=100`，固定按
`csv_row_number ASC, id ASC`。

上传人的 `createdBy` 只从已经验签的 `Jwt.getSubject()` 解析为正数
`Long`，不接受 multipart 字段、请求参数或用户名代替。

## 9. 错误映射与安全

新增统一错误码：

```text
INVALID_IMPORT_FILE
IMPORT_JOB_NOT_FOUND
```

映射：

- 请求级文件错误：`400`，超过 5 MiB 为 `413`；
- 任务不存在：`404`；
- 非法分页和 ID：沿用统一 `400`；
- 未预期处理失败：响应通用 `500`，数据库中的任务保存
  `PROCESSING_FAILED`；
- 缺少或非法 JWT：现有 `401`；
- REVIEWER 上传：现有 `403 ACCESS_DENIED`。

响应和日志不得记录文件字节、完整 CSV 行、JWT、密码、SQL 参数、
服务器路径或第三方解析器内部消息。

## 10. 测试与真实验收

### 10.1 单元与 Controller

- 文件准备结果防御性复制、哈希和旧入口兼容；
- 状态选择、统计去重和错误摘要；
- 首次 `201 + Location`、重复 `200`；
- 缺少/空/扩展名错误/超限文件；
- JWT subject 解析；
- 详情和错误分页 DTO；
- ADMIN、REVIEWER、匿名权限。

### 10.2 真实 MySQL

- 空库 V1→V5、V1～V4 校验和不变；
- 外键和来源关联约束；
- 全成功、部分成功、全部行错误；
- 五类文件级失败；
- 串行和并发同文件；
- 不同文件的跨文件重复；
- 已软删除 CSV 交易仍重复；
- 同业务键 MANUAL 与 CSV_IMPORT 可共存；
- 批量写入和交易唯一键竞态降级；
- 故障注入证明 TX-3 回滚、TX-4 独立失败恢复；
- 错误分页跨页稳定，无遗漏和重复。

### 10.3 最终验收

1. MySQL 为 `healthy`；
2. Day 5 聚焦测试通过；
3. `mvn clean test` 全部通过且无跳过；
4. 使用真实 ADMIN/REVIEWER JWT 完成 HTTP 权限和业务流；
5. SQL 验证任务、交易、行错误、外键和统计；
6. 清理测试数据、临时用户、文件、日志和 8080 端口；
7. `git diff --check` 与范围审计通过；
8. 仅在全部验收完成后提交
   `feat: implement synchronous CSV import`。
