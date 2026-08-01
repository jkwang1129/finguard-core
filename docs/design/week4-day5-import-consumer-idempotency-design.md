# Week 4 Day 5：导入消费幂等、并发所有权与崩溃恢复设计

## 1. 目标与结论

- **状态**：已完成。
- **业务目标**：RabbitMQ 至少一次投递造成重复、并发或 ACK 丢失时，同一导入任务只生成一份业务结果；消费者异常退出后任务能够重新处理。
- **当前基线**：Day 4 在一个数据库事务内完成 `PENDING -> PROCESSING -> 终态`、交易/行错误写入和统计，事务正常返回后 Listener 才手动 ACK。
- **设计结论**：Day 5 保留单事务处理，使用 `SELECT ... FOR UPDATE` 锁定一条 `import_jobs` 记录作为处理所有权；终态消息幂等短路；异常退出依靠数据库事务整体回滚和 RabbitMQ 未 ACK 红投恢复。
- **数据库结论**：本日不新增 V8。正常异步处理不会把中间态 `PROCESSING` 单独提交，因此没有可过期的持久化所有权，不需要伪造 token/lease 字段。

## 2. 为什么不新增处理租约

租约只在以下模型中有实际意义：

```text
TX-1: 提交 PROCESSING + owner token + lease deadline
进程执行较长业务
TX-2: 带 token 条件写终态
```

该模型能让查询接口观察到 `PROCESSING`，但同时引入租约续期、过期执行者 fencing、过期重领、重试消息延迟和旧执行者副作用隔离。Day 6 的有限重试队列尚未实现，提前拆开事务会产生“任务已提交为 PROCESSING，但红投消息在租约到期前反复进入主队列”的新失败窗口。

Day 4 当前模型是：

```text
TX:
  锁定任务行
  PENDING -> PROCESSING
  解析、校验、写交易和行错误
  PROCESSING -> 终态
COMMIT
basicAck
```

在该模型中：

- 其他事务只能看到提交前的 `PENDING` 或提交后的终态；
- 进程崩溃、连接断开或运行时异常会释放行锁并整体回滚到 `PENDING`；
- 未 ACK 消息由 RabbitMQ 红投，新的消费者可以重新取得行锁；
- 数据库提交后 ACK 丢失时，红投读取终态并幂等返回；
- 同一任务的并发消息在行锁上串行，后到者在锁释放后读取终态。

因此本日的所有权是数据库事务持有的行锁，恢复凭据是 `PENDING`/终态状态，而不是跨事务租约。若未来为了展示进度而让 `PROCESSING` 跨事务可见，必须新增迁移同时实现 token、lease、heartbeat/fencing 和有限延迟重试，不能只增加一个时间字段。

## 3. 状态决策表

消费者校验消息后，以 `aggregateId` 执行锁定读：

```sql
SELECT ...
FROM import_jobs
WHERE id = ?
FOR UPDATE
```

| 锁定后状态 | Service 动作 | 返回结果 | Listener 动作 |
|---|---|---|---|
| `PENDING` | 在同一事务内推进、处理并写终态 | `PROCESSED` | 事务提交后 ACK |
| `PROCESSING` | 视为旧同步流程或异常退出遗留；在当前行锁下恢复处理 | `RECOVERED` | 事务提交后 ACK |
| `SUCCESS` / `PARTIAL_SUCCESS` / `FAILED` | 不读取 CSV、不写业务表 | `ALREADY_COMPLETED` | ACK |
| 任务不存在 | 抛安全的任务不存在异常 | 无 | 不 ACK；Day 6 隔离 |
| 非法消息 | 不访问业务 Service | 无 | 不 ACK；Day 6 隔离 |
| 系统异常 | 整个业务事务回滚 | 无 | 不 ACK；Day 6 有限重试 |

可见的 `PROCESSING` 不可能来自 Day 5 正常并发消费者，因为正常处理的中间态未提交；它只可能来自 Week 3 的分段事务入口、人工故障注入或旧版本异常退出。Week 3 的业务事务同样保证交易/行错误整体回滚，因此在锁内恢复不会接管一份仍在提交的半套数据。

## 4. 消费结果与 ACK 边界

新增明确结果枚举：

```text
PROCESSED
RECOVERED
ALREADY_COMPLETED
```

Handler 校验消息后返回 Service 结果。三种结果都表示本条消息已经安全结束，因此 Listener 在 Handler 正常返回后执行：

```java
channel.basicAck(deliveryTag, false);
```

任何校验异常、任务不存在、数据库异常或 ACK 自身异常继续向 Listener Container 传播。Listener 不在 `finally` ACK，也不吞掉异常。

## 5. 四类故障时序

### 5.1 顺序重复投递

```text
message A -> 锁定 PENDING -> 写终态 -> COMMIT -> ACK
message A' -> 锁定终态 -> ALREADY_COMPLETED -> ACK
```

第二次不恢复文件、不解析 CSV、不新增交易或行错误。

### 5.2 并发重复投递

```text
consumer 1 -> SELECT FOR UPDATE -> 获得任务行锁 -> 处理
consumer 2 -> SELECT FOR UPDATE -> 等待同一任务行
consumer 1 -> COMMIT 终态
consumer 2 -> 读取终态 -> ALREADY_COMPLETED
```

锁只按任务 ID 生效；不同任务仍可并发消费。

### 5.3 数据库提交后 ACK 丢失

```text
业务事务 COMMIT 终态
basicAck 抛 IOException / Channel 断开
Broker 红投
消费者读取终态 -> ALREADY_COMPLETED -> ACK
```

交易业务唯一键仍是最终兜底，但正常红投不会再次触发插入。

### 5.4 消费者处理中崩溃

```text
获得任务行锁并写入未提交数据
进程/连接异常 -> MySQL ROLLBACK + 释放行锁
原消息未 ACK -> RabbitMQ 红投
新消费者锁定 PENDING -> 完整重新处理 -> COMMIT -> ACK
```

不得在异常路径单独提交 `PROCESSING_FAILED`，否则临时系统错误会绕过 Day 6 的有限重试策略。

## 6. 实现文件

- `ImportJobMapper`：新增按 ID 的 `FOR UPDATE` 完整行查询。
- `ImportJobTransactionService`：锁定后按状态分支并返回明确结果；保留 Week 3 分段事务方法供既有回归测试使用。
- `ImportProcessingResult`：表达首次处理、遗留恢复和终态重复。
- `ImportJobMessageHandler`：返回处理结果。
- `ImportJobMessageListener`：保持“Handler 正常返回后 ACK”，异常不 ACK。
- Day 5 聚焦测试：覆盖顺序重复、并发、ACK 丢失、事务回滚后红投和可见 `PROCESSING` 恢复。

## 7. 验收标准

- 同一任务顺序处理两次，结果为 `PROCESSED`、`ALREADY_COMPLETED`，业务记录不增加。
- 两线程并发处理同一任务，一个返回 `PROCESSED`，另一个返回 `ALREADY_COMPLETED`，数据库只有一份结果。
- 业务提交后模拟 `basicAck` 失败，再次调用 Listener 时只做终态短路并成功 ACK。
- 注入持久层异常后任务和业务记录回滚；解除异常再投递可成功完成。
- 人工构造已提交 `PROCESSING` 后可得到 `RECOVERED` 并完成，不永久卡住。
- 真实 RabbitMQ 两条相同任务消息最终清零，任务统计守恒。
- 完整测试、真实 HTTP/MySQL/RabbitMQ 验收、清理和 `git diff --check` 全部通过。

## 8. Day 6 交接边界

Day 5 不解决非法消息和持续系统故障的有限重试/隔离。Day 6 必须补齐两级 retry queue、TTL 退避、重试次数、DLQ 和对账消费者。当前异常继续不 ACK 是为了保持消息不丢失，不能宣传为完整重试能力。

## 9. 回滚

本日没有数据库迁移。回退结果枚举、锁定查询、Service/Handler 改造和测试即可恢复 Day 4 行为；V1～V7 及现有任务、文件、Outbox 和交易数据不变。
