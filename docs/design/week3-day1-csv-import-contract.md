# Week 3 Day 1：CSV 导入需求与契约设计

## 1. 目标与边界

本设计锁定 FinGuard Core 第一版 CSV 交易导入的输入、校验、幂等、失败处理、任务状态和接口语义，使后续数据库与同步导入实现有唯一依据。

第一版业务流程：

```text
ADMIN 上传 CSV
  → 请求级文件检查
  → 对原始文件字节计算 SHA-256
  → 创建导入任务
  → 解析和逐行校验
  → 正确行以 CSV_IMPORT 来源写入 transactions
  → 错误行形成可定位的行错误
  → 汇总 SUCCESS / PARTIAL_SUCCESS / FAILED
```

Day 1 只产出设计，不新增 Java 代码、依赖、Flyway 迁移或数据库表，不实现 RabbitMQ、Redis、自动对账、风险规则、审核和审计。

## 2. 文件契约

### 2.1 上传入口

后续同步版实现使用：

```http
POST /api/import-jobs
Content-Type: multipart/form-data
Authorization: Bearer <ADMIN JWT>
```

- 文件表单项固定命名为 `file`。
- 只有 `ADMIN` 可以上传；`REVIEWER` 和匿名用户不能上传。
- 上传接口同步处理文件并返回终态任务；Week 4 只把执行方式改为 RabbitMQ 异步，不改变本文件定义的数据规则。

### 2.2 文件级限制

| 项目 | 第一版决定 |
|---|---|
| 扩展名 | 必须为 `.csv`，大小写不敏感 |
| MIME | 只作辅助判断，不能单独作为可信依据 |
| 编码 | UTF-8；允许文件开头存在一个 UTF-8 BOM |
| 最大文件大小 | 5 MiB，即 `5 * 1024 * 1024` 字节 |
| 最大数据记录数 | 10,000 条，不包含表头 |
| 最少数据记录数 | 1 条 |
| 分隔符 | 半角逗号 `,` |
| 记录换行 | 接受 CRLF 或 LF，不接受裸 CR |
| 引号 | 双引号 `"` |
| 引号转义 | 引号字段内使用 `""` 表示一个双引号 |
| 注释行 | 不支持 |
| 空白记录 | 数据区中的空白记录按错误行处理，不静默忽略 |
| 原文件保存 | 第一版不保存原始文件，只保存任务元数据、哈希和必要错误信息 |

MIME 可能因浏览器和操作系统不同而变化，因此实现时以扩展名、大小、UTF-8 解码和 CSV 结构校验为准，不能只相信客户端上传的 `Content-Type`。

### 2.3 CSV 语法

采用 RFC 4180 常用语义：

- 未加引号的字段不能包含逗号、双引号或换行。
- 包含逗号、双引号或换行的字段必须放在双引号内。
- 引号字段内的双引号必须写成两个连续双引号。
- 最后一条记录可以有或没有行结束符。
- 解析必须使用成熟 CSV 库，禁止使用 `String.split(",")`。

“行号”定义为 CSV 逻辑记录号：表头是第 1 条记录，第一条数据是第 2 条记录。带换行的引号字段仍是一条逻辑记录，错误定位使用逻辑记录号而不是物理文本行号。

### 2.4 固定表头

表头必须完整、无重复、区分大小写，并严格采用以下名称和顺序：

```text
account_no,external_transaction_no,direction,amount,transaction_time,description
```

不接受：

- 缺少列；
- 多余列；
- 调换顺序；
- 表头前后空格；
- 中文表头；
- 不同大小写。

严格表头使示例、校验、错误定位和后续批处理保持一致，避免第一版引入动态列映射。

### 2.5 正确示例

```csv
account_no,external_transaction_no,direction,amount,transaction_time,description
BANK-001,EXT-20260731-0001,INCOME,1200.50,2026-07-31 09:30:15,"July salary"
BANK-001,EXT-20260731-0002,EXPENSE,88.00,2026-07-31 12:10:00,"Lunch, client meeting"
PAYMENT_01,EXT-20260731-0003,EXPENSE,20,2026-07-31 18:05:30,"He said ""thanks"""
```

第二条数据的描述含逗号，第三条数据的描述含双引号，因此必须使用正确的 CSV 引号与转义。

### 2.6 文件级失败示例

表头顺序错误：

```csv
external_transaction_no,account_no,direction,amount,transaction_time,description
EXT-001,BANK-001,INCOME,10.00,2026-07-31 09:30:15,test
```

引号没有闭合：

```csv
account_no,external_transaction_no,direction,amount,transaction_time,description
BANK-001,EXT-001,INCOME,10.00,2026-07-31 09:30:15,"unclosed
```

这两种情况都不能可靠地按照既定结构解释整个文件，属于文件级失败。

## 3. 字段映射与校验

### 3.1 总体规则

- 每条数据记录必须恰好包含 6 个字段；缺列或多列都是该记录的行级错误。
- 字段解析后先按下表规范化，再校验，再查询账户和检查重复。
- 一个字段失败后，该记录不写入 `transactions`；实现可以继续收集同一记录的其他字段错误，但至少要保存一个稳定错误码。
- `source` 不允许出现在 CSV 中，由服务端固定为 `CSV_IMPORT`。
- CSV 导入使用现有金额、时间和描述规则，不创建第二套互相矛盾的交易规则。

### 3.2 字段定义

| CSV 字段 | 目标 | 必填 | 规范化 | 校验 |
|---|---|---:|---|---|
| `account_no` | 通过账户号查找 `accounts.id` | 是 | 去除首尾空白后按 `Locale.ROOT` 转大写 | 3～64 个字符；只允许大写字母、数字、`-`、`_`；账户必须存在、未软删除且为 `ACTIVE` |
| `external_transaction_no` | `transactions.external_transaction_no` | 是 | 去除首尾空白，保留原有大小写 | 1～128 个 Java 字符；数据库使用二进制排序规则，因此大小写敏感 |
| `direction` | `transactions.direction` | 是 | 去除首尾空白后按 `Locale.ROOT` 转大写 | 只能是 `INCOME` 或 `EXPENSE` |
| `amount` | `transactions.amount` | 是 | 按十进制文本解析为 `BigDecimal`，成功后精确补为两位小数 | 必须大于 0；最多 17 位整数和 2 位小数；不允许千分位、货币符号、正号、负号、指数或舍入 |
| `transaction_time` | `transactions.transaction_time` | 是 | 按固定格式解析为 `LocalDateTime` | 格式必须为 `uuuu-MM-dd HH:mm:ss`；按 `Asia/Shanghai` 解释；不得晚于当前业务时间 5 分钟以上 |
| `description` | `transactions.description` | 否 | 去除首尾空白；空字符串转为 `null` | 最多 255 个 Java 字符；逗号、引号或换行必须遵守 CSV 引号规则 |

金额文本接受范围可用下面的规则理解：

```text
^(0|[1-9][0-9]{0,16})(\.[0-9]{1,2})?$
```

正则匹配后还必须保证数值大于 0。因此 `1`、`1.2`、`1.20`、`0.01` 合法，`0`、`1.234`、`1,000.00`、`1e3`、`+1.00`、`-1.00` 不合法。系统只补齐小数位，不进行四舍五入。

### 3.3 与现有业务规则的兼容性

- 账户号沿用 `AccountServiceImpl` 的 `trim + uppercase` 规则。
- 外部流水号沿用 `TransactionServiceImpl` 的 `trim` 规则，不自动修改大小写。
- 金额沿用 `DECIMAL(19,2)`、正数、最多两位小数和 `RoundingMode.UNNECESSARY` 规则。
- 时间沿用 `Asia/Shanghai` 业务时区以及“最多允许未来 5 分钟”的规则。
- 描述沿用 `trim`、空值转 `null`、最多 255 字符的规则。
- CSV 导入交易写入后不能通过现有人工交易修改和删除接口操作，因为这些接口只允许 `MANUAL` 来源。

### 3.4 行级错误示例

```csv
account_no,external_transaction_no,direction,amount,transaction_time,description
BANK-001,EXT-001,INCOME,10.00,2026-07-31 09:30:15,valid
UNKNOWN,EXT-002,EXPENSE,20.00,2026-07-31 10:00:00,account missing
BANK-001,EXT-003,EXPENSE,12.345,2026-07-31 11:00:00,too many decimals
BANK-001,EXT-004,OUT,8.00,2026-07-31 12:00:00,bad direction
```

第一条数据可以写入；后三条分别形成账户不存在、金额格式错误和方向错误，不应破坏第一条正确数据。

## 4. 幂等与重复策略

### 4.1 文件级 SHA-256

SHA-256 对客户端上传的原始字节计算，必须在移除 BOM、解码文本、统一换行或修剪字段之前完成。

```text
fileHash = lowercaseHex(SHA-256(originalBytes))
```

- 结果固定为 64 个小写十六进制字符。
- `import_jobs.file_hash` 必须有数据库唯一约束，应用层预查询只用于友好返回，唯一约束负责并发兜底。
- 同一字节文件重复上传时不创建新任务、不重新解析、不重复写入交易，而是返回原任务。
- 首次接受并创建任务返回 `201 Created`；命中已有哈希返回 `200 OK`，响应中的 `duplicateFile=true`，任务 ID 和结果都来自已有任务。
- 若两个相同文件并发上传，只有一个请求能创建任务；另一个捕获唯一键冲突后查询并返回已存在任务。

SHA-256 判断的是“字节完全相同”，不是“业务内容等价”。相同 CSV 改变 BOM、换行或空白后哈希可能不同，后续仍由交易业务唯一键阻止重复交易。

### 4.2 交易业务唯一键

现有数据库约束为：

```text
(account_id, source, external_transaction_no)
```

CSV 交易的 `source` 固定为 `CSV_IMPORT`，因此实际唯一键为：

```text
(resolvedAccountId, CSV_IMPORT, normalizedExternalTransactionNo)
```

职责边界：

- 文件哈希防止同一个原始文件被重复处理。
- 交易唯一键防止不同文件重复携带同一条 CSV 业务交易。
- `MANUAL` 与 `CSV_IMPORT` 来源不同，可以存在相同账户和外部流水号，供后续自动对账比较。
- 外部流水号大小写敏感，`EXT-001` 与 `ext-001` 是两个不同业务键。
- 已软删除交易仍占用唯一键，不能通过删除后重传绕过去。

### 4.3 文件内重复

按照 CSV 记录顺序处理：

1. 完成字段校验并解析到账户 ID。
2. 生成规范化业务唯一键。
3. 同一文件中第一条校验通过的业务键可以继续处理。
4. 后续相同业务键记录为 `DUPLICATE_TRANSACTION_IN_FILE`，不写入交易。

如果较早记录本身字段错误、无法形成有效业务键，不占用“第一条有效记录”的位置。

### 4.4 跨文件和数据库重复

对于哈希不同但业务键已存在的记录：

- 应用层可先批量查询已有业务键，减少无意义插入。
- 数据库唯一约束仍是最终兜底，必须捕获并映射为稳定的行错误。
- 该行记录为 `DUPLICATE_TRANSACTION`，不更新、不覆盖、不软删除已有交易。
- 其他非重复正确行继续处理，因此任务可能为 `PARTIAL_SUCCESS`。

第一版不提供强制覆盖、重复文件重新执行、失败任务重试或手工忽略唯一约束的能力。这些操作会削弱幂等边界，后续只有出现明确业务需求时才单独设计。

## 5. 失败层级与错误契约

### 5.1 请求级拒绝

以下问题在创建导入任务之前拒绝：

| 场景 | HTTP | 结果 |
|---|---:|---|
| 未认证 | 401 | 不创建任务 |
| 非 ADMIN 上传 | 403 | 不创建任务 |
| 缺少 `file` 表单项 | 400 | 不创建任务 |
| 文件名缺失或扩展名不是 `.csv` | 400 | 不创建任务 |
| 文件为 0 字节 | 400 | 不创建任务 |
| 文件超过 5 MiB | 413 | 不创建任务 |
| 读取上传流或计算哈希失败 | 500 | 不创建任务，返回通用内部错误 |

这些错误继续使用统一 REST 错误响应，不泄露服务器路径、解析器异常或数据库细节。

### 5.2 文件级失败

基本请求检查通过后，系统计算哈希、创建 `PENDING` 任务并开始处理。以下问题使已创建任务进入 `FAILED`：

| 错误码 | 场景 |
|---|---|
| `INVALID_UTF8` | 文件不能按 UTF-8 严格解码，或 BOM 不合法 |
| `INVALID_HEADER` | 表头缺失、重复、增减、改名或顺序不符 |
| `MALFORMED_CSV` | 引号或记录边界损坏，无法可靠继续解析 |
| `NO_DATA_ROWS` | 只有表头，没有数据记录 |
| `TOO_MANY_ROWS` | 数据记录超过 10,000 条 |

文件级失败不写入任何交易，也不产生普通行错误；任务保存一个文件级错误码和对用户安全的摘要。因为任务资源已经创建，同步上传响应仍返回 `201 Created` 和 `status=FAILED`，客户端必须读取任务状态，不能只看 HTTP 是否为 2xx。

### 5.3 行级错误

CSV 结构仍可继续解析，但单条记录不能入库时，记录行错误并继续处理其他记录：

| 错误码 | 默认字段 | 场景 |
|---|---|---|
| `COLUMN_COUNT_MISMATCH` | `row` | 该逻辑记录不是 6 个字段，包括空白记录 |
| `INVALID_ACCOUNT_NO` | `account_no` | 为空、格式或长度不合法 |
| `ACCOUNT_NOT_FOUND` | `account_no` | 规范化后找不到未删除账户 |
| `ACCOUNT_NOT_ACTIVE` | `account_no` | 账户存在但不是 `ACTIVE` |
| `INVALID_EXTERNAL_TRANSACTION_NO` | `external_transaction_no` | 为空或超过 128 字符 |
| `INVALID_DIRECTION` | `direction` | 不是 `INCOME` 或 `EXPENSE` |
| `INVALID_AMOUNT` | `amount` | 格式、范围、精度或符号不合法 |
| `INVALID_TRANSACTION_TIME` | `transaction_time` | 格式不符或晚于业务时间 5 分钟以上 |
| `DESCRIPTION_TOO_LONG` | `description` | 规范化后超过 255 字符 |
| `DUPLICATE_TRANSACTION_IN_FILE` | `external_transaction_no` | 同一文件内出现重复业务键 |
| `DUPLICATE_TRANSACTION` | `external_transaction_no` | 数据库中已经存在该 CSV 业务键 |

每条行错误至少保存：

```text
importJobId
rowNumber
field
errorCode
rejectedValue
message
```

- `rowNumber` 使用 CSV 逻辑记录号。
- `rejectedValue` 只保存当前错误字段，最多 255 个字符，超出部分截断并标记；不保存整条原始记录。
- `message` 是稳定、可读且不包含堆栈、SQL、服务器路径或敏感数据的说明。
- 同一记录可以保存多个字段错误，但 `failedRows` 对该记录只计数一次。

### 5.4 数据错误与系统错误的事务边界

- 行级业务错误是预期结果，不通过抛出异常回滚其他正确行。
- 在同步版中，正确交易、行错误和最终统计必须在一个受控处理事务内保持一致。
- 若任务创建后出现未预期的数据库或运行时异常，本次处理产生的交易和行错误整体回滚，再使用独立事务把任务标记为 `FAILED`，文件错误码使用 `PROCESSING_FAILED`。
- 任何情况下都不能出现任务显示 `SUCCESS`，但交易只写入一部分的状态。

## 6. 导入任务状态与统计

### 6.1 状态流转

```text
PENDING → PROCESSING → SUCCESS
                     ↘ PARTIAL_SUCCESS
                     ↘ FAILED
```

- 只允许上述正向流转。
- `SUCCESS`、`PARTIAL_SUCCESS`、`FAILED` 是不可再次处理的终态。
- 相同文件重复上传只返回原任务，不触发终态回到 `PROCESSING`。
- 第一版不引入状态机框架，由枚举和 Service 层状态校验实现。

### 6.2 终态定义

| 状态 | 精确定义 |
|---|---|
| `SUCCESS` | 至少有 1 条数据记录，所有记录都成功写入，`failedRows=0` |
| `PARTIAL_SUCCESS` | 至少 1 条记录成功写入，至少 1 条记录因行级错误被拒绝 |
| `FAILED` | 文件级失败、系统级失败，或者所有数据记录均被拒绝且 `successRows=0` |

对完成解析的文件必须满足：

```text
totalRows = successRows + failedRows
```

其中：

- `totalRows`：表头之后的逻辑数据记录数，包括错误记录和空白记录；
- `successRows`：实际成功写入的新 CSV 交易数；
- `failedRows`：至少有一个行错误而未写入的记录数；
- `duplicateRows`：`failedRows` 中因文件内或数据库重复被拒绝的记录数。

### 6.3 任务摘要

Day 2 设计 `import_jobs` 时至少考虑以下业务属性：

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
```

`errorSummary` 只保存文件级摘要或“首个错误 + 错误总数”一类短摘要。完整行错误通过分页接口查询，避免把所有错误拼进一个数据库字段或上传响应。

## 7. 接口与响应草案

### 7.1 权限矩阵

| 接口 | ADMIN | REVIEWER | 匿名 |
|---|---:|---:|---:|
| `POST /api/import-jobs` | 允许 | 403 | 401 |
| `GET /api/import-jobs/{id}` | 允许 | 允许 | 401 |
| `GET /api/import-jobs/{id}/errors` | 允许 | 允许 | 401 |

上传属于写操作，沿用 ADMIN-only 规则；任务和错误查询属于只读操作，允许 REVIEWER。

### 7.2 上传响应

首次上传并创建任务：

```http
HTTP/1.1 201 Created
Location: /api/import-jobs/101
```

```json
{
  "id": 101,
  "originalFileName": "transactions-20260731.csv",
  "fileHash": "64-lowercase-hex-characters",
  "status": "PARTIAL_SUCCESS",
  "totalRows": 3,
  "successRows": 2,
  "failedRows": 1,
  "duplicateRows": 0,
  "errorSummary": "1 row rejected",
  "duplicateFile": false
}
```

相同字节文件再次上传：

```http
HTTP/1.1 200 OK
```

返回同一个任务 ID、状态和统计，并将 `duplicateFile` 设为 `true`。

### 7.3 错误明细查询

```http
GET /api/import-jobs/{id}/errors?page=1&size=20
```

- 默认每页 20，最大 100。
- 按 `rowNumber ASC, id ASC` 稳定排序。
- 任务不存在返回统一 `404`。
- 上传响应和任务详情最多内联展示前 20 个错误；完整集合必须分页查询。

## 8. 验收测试矩阵

后续实现至少覆盖下列场景。测试名称可以调整，但业务预期不能静默改变。

### 8.1 文件和解析

| 场景 | 预期 |
|---|---|
| 标准 UTF-8、CRLF 文件 | 创建任务并正常处理 |
| UTF-8 BOM、LF 文件 | 创建任务并正常处理 |
| 描述含逗号、双引号或引号内换行 | 正确解析为一个字段和一条逻辑记录 |
| 缺少文件、空文件、错误扩展名 | 请求级拒绝，不创建任务 |
| 文件超过 5 MiB | `413`，不创建任务 |
| 非 UTF-8 | 创建任务后 `FAILED/INVALID_UTF8`，无交易 |
| 表头缺失、错序、增列或改名 | `FAILED/INVALID_HEADER`，无交易 |
| 引号未闭合 | `FAILED/MALFORMED_CSV`，无交易 |
| 只有表头 | `FAILED/NO_DATA_ROWS`，无交易 |
| 超过 10,000 条数据记录 | `FAILED/TOO_MANY_ROWS`，无交易 |

### 8.2 字段和行错误

| 场景 | 预期 |
|---|---|
| 合法账户号带小写和首尾空格 | 规范化为大写后找到 ACTIVE 账户 |
| 账户不存在、已删除或已禁用 | 对应行错误，不写入该行 |
| 流水号首尾有空格 | 去除首尾空格，内部字符和大小写保留 |
| 金额 `1`、`1.2`、`1.20` | 分别规范化为 `1.00`、`1.20`、`1.20` |
| 金额为 0、负数、3 位小数、千分位或指数 | `INVALID_AMOUNT` |
| 方向为小写并带空格 | 规范化后接受 |
| 时间格式错误或超过未来 5 分钟 | `INVALID_TRANSACTION_TIME` |
| 描述为空或全空格 | 写入 `null` |
| 描述超过 255 字符 | `DESCRIPTION_TOO_LONG` |
| 空白数据记录或字段数量不是 6 | `COLUMN_COUNT_MISMATCH` |

### 8.3 幂等和并发

| 场景 | 预期 |
|---|---|
| 同一原始文件串行上传两次 | 第二次返回原任务，交易不增加 |
| 同一原始文件并发上传两次 | 唯一哈希保证只有一个任务 |
| 相同业务内容但换行或 BOM 不同 | 文件哈希可不同，交易唯一键仍防重复 |
| 文件内两条相同有效业务键 | 第一条处理，后续为 `DUPLICATE_TRANSACTION_IN_FILE` |
| 不同文件包含已存在 CSV 业务键 | 重复行为 `DUPLICATE_TRANSACTION` |
| MANUAL 与 CSV_IMPORT 使用相同账户和流水号 | 两条均可存在，来源不同 |
| 已软删除 CSV 交易再次导入 | 仍判定重复 |

### 8.4 状态、统计和事务

| 场景 | 预期 |
|---|---|
| 全部记录成功 | `SUCCESS`，失败数为 0 |
| 正确记录和错误记录并存 | `PARTIAL_SUCCESS`，正确行保留 |
| 所有记录都是行错误 | `FAILED`，成功数为 0 |
| 文件级错误 | `FAILED`，无交易和普通行错误 |
| 处理中发生未预期数据库异常 | 业务写入回滚，任务最终为 `FAILED` |
| 多个错误落在同一记录 | `failedRows` 只增加 1 |
| 完成解析 | `totalRows = successRows + failedRows` |

### 8.5 安全与查询

| 场景 | 预期 |
|---|---|
| ADMIN 上传 | 允许 |
| REVIEWER 上传 | `403`，不创建任务 |
| 匿名上传或查询 | `401` |
| REVIEWER 查询任务和错误 | 允许 |
| 查询不存在任务 | 统一 `404` |
| 错误分页超过最大 size | 统一参数错误 |
| 内部异常 | 响应不包含 SQL、路径、堆栈或原始整行 |

## 9. 后续实现交接

Day 2 进入数据库设计时，以本契约为输入：

1. 设计 `import_jobs` 和 `import_row_errors`，不要提前创建对账、审核、审计或 MQ 表。
2. 为文件哈希建立数据库唯一约束，并定义任务状态、错误码枚举和必要外键。
3. 明确任务统计字段和行错误分页所需索引。
4. 使用新的 Flyway 迁移，禁止修改已经应用的 V1～V3。
5. 用空库迁移、约束和 Mapper 集成测试验收 Day 2。

后续同步导入实现必须复用现有账户和交易规则，不能绕过数据库唯一约束，也不能让 Controller 直接承担解析、幂等和事务逻辑。

## 10. Day 1 决策结论

- [x] 文件编码、表头、字段顺序、大小、行数和 CSV 语法已锁定。
- [x] 六个字段的映射、规范化、校验和现有业务兼容性已锁定。
- [x] SHA-256 文件幂等和交易业务唯一键职责已分离。
- [x] 文件级失败、行级隔离、重复记录和部分成功策略已锁定。
- [x] 任务状态、统计、错误摘要、接口和验收测试矩阵已锁定。
- [x] Day 1 未实现 RabbitMQ、Redis、自动对账或任何业务代码。
