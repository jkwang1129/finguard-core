# Week 3 Day 3：CSV 文件指纹与结构解析设计

## 1. 业务目标与范围

Day 3 把客户端提供的文件名和原始字节转换为一个纯内存解析结果：

```text
originalFileName + originalBytes
  → 请求级文件检查
  → 对原始字节计算 SHA-256
  → 严格 UTF-8 / BOM / 换行检查
  → Apache Commons CSV 结构解析
  → 固定表头与记录数检查
  → ParsedImportFile
```

`ParsedImportFile` 包含原文件名、原始字节大小、64 位小写 SHA-256 和带 CSV 逻辑记录号的原始数据行。它是 Day 4 逐字段规范化、业务校验和重复判断的输入。

本日不开放 HTTP 上传端点，不访问数据库，不创建或更新导入任务，不查询账户，不写入交易，也不实现 RabbitMQ、Redis 或自动对账。

## 2. 组件职责

### 2.1 `CsvImportFileParser`

这是 Day 3 唯一的公开入口：

```java
ParsedImportFile parse(String originalFileName, byte[] originalBytes)
```

执行顺序固定为：

1. 校验文件名、扩展名和原始字节大小；
2. 对未经修改的原始字节计算 SHA-256；
3. 严格解码 UTF-8，并只移除文件开头的一个 BOM；
4. 拒绝错误位置 BOM 和裸 `CR`；
5. 使用 Apache Commons CSV 解析逻辑记录；
6. 校验固定表头和 1～10,000 条数据记录；
7. 返回不可变解析结果。

解析器不修剪字段、不转换大小写、不解析金额或时间，也不查询账户。这样能够保证 Day 3 只负责“文件能否被可靠拆成记录”，不会和 Day 4 的业务规则混合。

### 2.2 `ParsedImportFile`

不可变文件级结果，字段为：

- `originalFileName`：保留客户端原文件名；
- `fileHash`：对原始字节计算的 64 位小写 SHA-256；
- `fileSizeBytes`：原始字节数；
- `rows`：不可变数据行列表。

### 2.3 `ParsedCsvRow`

不可变行级结果，字段为：

- `rowNumber`：CSV 逻辑记录号，表头为 1，第一条数据为 2；
- `values`：该逻辑记录的原始字段列表。

`values` 不强制为六项。列数错误属于 Day 1 已定义的行级错误 `COLUMN_COUNT_MISMATCH`，因此 Day 3 必须保留该记录并交给 Day 4，而不能把整个文件判为失败。

## 3. 请求级拒绝

以下问题发生在产生可持久化任务结果之前，抛出 `InvalidImportFileRequestException`：

| 代码 | 条件 | 后续 HTTP 语义 |
|---|---|---:|
| `MISSING_FILE_NAME` | 文件名为 `null`、空或仅空白 | 400 |
| `INVALID_FILE_EXTENSION` | 文件名不以 `.csv` 结尾，大小写不敏感 | 400 |
| `EMPTY_FILE` | 原始字节为 `null` 或长度为 0 | 400 |
| `FILE_TOO_LARGE` | 原始字节超过 5 MiB | 413 |

异常只携带稳定代码和安全消息。Day 3 不加入 HTTP 状态或统一异常处理映射。

## 4. 文件级失败

通过请求级检查后，以下问题抛出 `ImportFileParseException`，并携带现有 `ImportFileErrorCode`：

| 错误码 | 条件 |
|---|---|
| `INVALID_UTF8` | UTF-8 字节非法、出现多个 BOM 或 BOM 不在文件开头 |
| `INVALID_HEADER` | 表头缺失，或名称、大小写、顺序、数量不完全一致 |
| `MALFORMED_CSV` | 裸 `CR`、引号未闭合或 CSV 记录边界损坏 |
| `NO_DATA_ROWS` | 合法表头后没有数据记录 |
| `TOO_MANY_ROWS` | 数据记录达到第 10,001 条 |

异常不转发第三方解析器消息、服务器路径或原始堆栈内容。`PROCESSING_FAILED` 保留给后续编排阶段的非预期系统失败，Day 3 不主动产生该代码。

## 5. CSV 解析决定

- 使用 Apache Commons CSV 1.14.1；它是当前稳定发布版并支持 Java 8 以上。
- 基于 `CSVFormat.RFC4180`，显式设置 `ignoreEmptyLines=false`，使空白记录不会被静默跳过。
- 不启用自动修剪、忽略大小写、注释或动态表头。
- 第一条逻辑记录由应用代码与固定表头逐项比较：

```text
account_no,external_transaction_no,direction,amount,transaction_time,description
```

- 使用 `CSVRecord.getRecordNumber()` 作为逻辑记录号；由于表头也由同一个解析器读取，第一条数据记录号自然为 2。
- 解析到第 10,001 条数据记录时立即停止并返回 `TOO_MANY_ROWS`，不继续无界读取。
- `CSVParser` 在 `try-with-resources` 中关闭。

## 6. 编码、BOM、换行与哈希

- 先对 `originalBytes` 计算 SHA-256，再执行任何解码或移除 BOM 操作。
- 只允许字节开头存在一次 UTF-8 BOM：`EF BB BF`。
- 移除开头 BOM 后，使用 `CharsetDecoder` 的 `REPORT` 模式严格解码；不允许用替换字符掩盖错误字节。
- 解码结果中的任何 `U+FEFF` 都表示 BOM 位置非法。
- `LF` 和 `CRLF` 合法；任意没有紧跟 `LF` 的 `CR` 都映射为 `MALFORMED_CSV`。
- 相同原始字节必须得到相同哈希；BOM、换行或空白发生字节级变化时，哈希必须改变。

## 7. 测试设计

聚焦单元测试不启动 Spring，也不访问 MySQL，至少覆盖：

- 普通 LF 与 CRLF 文件；
- UTF-8 BOM；
- 描述中的逗号、转义双引号和引号内换行；
- 空白记录与列数不足记录被保留；
- 逻辑记录号在多行字段后仍正确；
- 哈希确定性及 BOM、换行、空白变化；
- 缺失文件名、错误扩展名、空文件和超过 5 MiB；
- 非法 UTF-8、重复或错误位置 BOM、裸 `CR`；
- 缺失、增减、换序、改名和大小写错误的表头；
- 未闭合引号；
- 只有表头；
- 10,000 条数据成功，第 10,001 条立即失败；
- 解析结果和行列表不可修改。

完成聚焦测试后运行完整 `mvn clean test`，再执行应用健康检查、端口清理、`git diff --check` 和范围审计。

## 8. Day 4 交接边界

Day 4 接收 `ParsedCsvRow`，实现：

- 六列数量校验；
- 字段修剪和规范化；
- 账户号、流水号、方向、金额、时间和描述校验；
- 账户存在/状态查询；
- 文件内和数据库交易重复判断；
- 行错误对象生成。

Day 4 不应重新读取原始字节、重新计算文件哈希或重新解释 CSV 引号与记录边界。
