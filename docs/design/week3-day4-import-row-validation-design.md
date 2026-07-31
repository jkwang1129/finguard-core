# Week 3 Day 4：CSV 逐行规范化与业务校验设计

## 1. 业务目标与边界

Day 4 接收 Day 3 的 `ParsedImportFile`，输出不可变的文件校验结果：

```text
ParsedImportFile
  → 本地字段规范化和校验
  → 批量解析账户
  → 文件内重复判断
  → 批量查询数据库重复
  → ImportFileValidationResult
```

结果包含可供 Day 5 入库的 `ValidatedImportRow` 和可转成
`ImportRowError` 实体的 `ImportRowValidationError`。Day 4 只读
`accounts` 和 `transactions`，不写任何业务表，不创建 HTTP 接口，
不推进导入任务状态，也不处理批量插入或数据库唯一键竞态。

## 2. 组件与结果模型

### 2.1 `CsvImportRowValidator`

这是 Day 4 的公开入口：

```java
ImportFileValidationResult validate(ParsedImportFile parsedFile)
```

它负责整文件校验顺序、批量账户解析、文件内重复和数据库重复判断。
方法使用只读事务，输入列表和输出列表均不可修改。

### 2.2 `CsvImportRowFieldValidator`

这是不访问数据库的纯字段校验组件。它：

- 先检查记录是否恰好六列；
- 按固定字段顺序规范化并收集本地错误；
- 使用注入的 `Clock` 校验业务时间；
- 只在本地字段全部合法时产生待解析账户的候选。

固定字段顺序为：

```text
account_no
external_transaction_no
direction
amount
transaction_time
description
```

### 2.3 结果对象

- `ValidatedImportRow`：逻辑记录号、账户 ID、流水号、方向、金额、
  交易时间、描述和固定来源 `CSV_IMPORT`。
- `ImportRowValidationError`：逻辑记录号、字段名、稳定错误码、
  最多 255 字符的拒绝值和安全消息。
- `ImportFileValidationResult`：原文件数据记录数、合法候选列表和
  错误列表，并可计算失败记录数与重复记录数。

一条记录不能同时出现在合法候选和错误集合中。同一记录可有多个
错误，但失败记录数按不同逻辑记录号计算。

## 3. 本地字段规则

| 字段 | 规范化 | 本地校验 |
|---|---|---|
| `account_no` | `trim` 后按 `Locale.ROOT` 转大写 | `^[A-Z0-9][A-Z0-9_-]{2,63}$` |
| `external_transaction_no` | `trim`，保留大小写 | 1～128 个 Java 字符 |
| `direction` | `trim` 后按 `Locale.ROOT` 转大写 | `INCOME` 或 `EXPENSE` |
| `amount` | 严格十进制文本解析并补为两位小数 | 大于 0，最多 17 位整数和 2 位小数，不允许符号、指数、千分位或舍入 |
| `transaction_time` | 严格解析 `uuuu-MM-dd HH:mm:ss` | 不晚于注入业务时间的未来 5 分钟 |
| `description` | `trim`，空串转 `null` | 最多 255 个 Java 字符 |

金额文本先匹配：

```text
^(0|[1-9][0-9]{0,16})(\.[0-9]{1,2})?$
```

再转换为 `BigDecimal`、检查大于零并使用
`setScale(2, RoundingMode.UNNECESSARY)`。时间解析使用
`ResolverStyle.STRICT`，合法 CSV 格式精确到秒，因此结果纳秒为零。

列数不是六列时只产生 `COLUMN_COUNT_MISMATCH`，不继续按字段下标
读取。其他情况下，本地字段错误按上表顺序稳定输出。

## 4. 账户解析与重复优先级

### 4.1 账户

只对格式合法的规范化账户号查询数据库。查询先对账户号去重，再按
固定批大小分片，避免最多 10,000 条输入形成逐行 N+1：

- 查询不到未删除账户：`ACCOUNT_NOT_FOUND`；
- 查询到 `DISABLED` 账户：`ACCOUNT_NOT_ACTIVE`；
- 查询到 `ACTIVE` 账户：解析出 `accountId`。

软删除账户不会由查询返回，因此和不存在账户使用相同错误语义。

### 4.2 文件内重复

只有本地字段全部合法且账户为 `ACTIVE` 的记录才能形成：

```text
(accountId, CSV_IMPORT, externalTransactionNo)
```

按 CSV 逻辑记录号顺序处理。第一个有效业务键占位，后续相同键产生
`DUPLICATE_TRANSACTION_IN_FILE`。较早但无法形成有效业务键的记录
不占位。

### 4.3 数据库重复

完成文件内去重后，对保留的业务键去重并分批查询。查询必须：

- 固定 `source = CSV_IMPORT`；
- 不过滤软删除交易；
- 依赖 `external_transaction_no` 的二进制排序规则保持大小写敏感；
- 不把相同账户和流水号的 `MANUAL` 交易当成重复。

命中后产生 `DUPLICATE_TRANSACTION`。未命中的记录才进入合法候选。
Day 5 插入时仍必须捕获数据库唯一键竞态；Day 4 预查询不是最终并发
保证。

## 5. 行错误安全规则

- 字段名只能使用 V4 检查约束允许的七种固定值。
- 消息来自代码中的固定安全文本，不拼接 SQL、路径、堆栈或第三方
  异常。
- 拒绝值只保存当前字段；不保存整条 CSV 记录。
- 不超过 255 字符的拒绝值原样保留；超长值保留前 252 个字符并追加
  `...`，使数据库值仍不超过 255 字符。
- 重复错误使用规范化后的流水号作为拒绝值。

## 6. 批量读取策略

账户号和交易业务键分别先去重，再按每批 500 个元素查询。该大小
把单条 SQL 的参数数量控制在可解释范围内，也远低于 MySQL 参数和
报文上限。

账户查询使用现有唯一键 `uk_accounts_account_no`。交易查询使用现有
唯一键 `uk_transactions_account_source_external_no`。Day 4 先以真实
SQL 和 `EXPLAIN` 验证访问路径；没有证据不新增迁移或索引。

## 7. 测试与验收

### 7.1 纯单元测试

- 六列和列数错误；
- 六个字段的规范化、边界和非法值；
- 金额禁止舍入、符号、指数和千分位；
- 固定 `Clock` 下恰好未来五分钟合法、超过一秒非法；
- 多个本地错误顺序稳定；
- 拒绝值截断和结果不可修改。

### 7.2 Mapper 与整文件集成测试

- ACTIVE、DISABLED、不存在和软删除账户；
- 文件内重复的第一条有效记录规则；
- 已存在和已软删除的 `CSV_IMPORT` 交易；
- 相同键的 `MANUAL` 交易不算重复；
- `EXT-001` 与 `ext-001` 大小写敏感；
- 大于单批大小的输入确实分片且结果完整；
- 校验前后四张相关表的行数不变。

完成聚焦测试后运行 `mvn clean test`，再执行真实 MySQL 查询计划、
应用健康检查、测试数据和端口清理、`git diff --check` 与范围审计。

## 8. Day 5 交接

Day 5 接收 `ParsedImportFile` 和 `ImportFileValidationResult`，负责：

- 创建导入任务并处理文件哈希幂等；
- 推进 `PENDING → PROCESSING → 终态`；
- 把合法候选批量写入 `transactions`；
- 把校验错误转换并写入 `import_row_errors`；
- 汇总任务统计和错误摘要；
- 用受控事务保持交易、行错误和终态一致；
- 捕获数据库唯一键竞态并映射为行错误。

Day 4 不提前实现上述职责。
