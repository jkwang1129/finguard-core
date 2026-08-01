# Week 4 Day 4：异步导入消费者与手动 ACK 设计

## 1. 目标与边界

- **业务目标**：消费 `finguard.import.queue` 中的 `IMPORT_REQUESTED` 消息，从 MySQL 读取原始 CSV，复用 Week 3 导入规则，并在数据库事务成功提交后手动 ACK。
- **当前输入**：Day 3 已原子保存 `PENDING import_jobs`、`import_job_files` 和 Outbox，并将稳定 JSON 消息发布到 RabbitMQ。
- **本日交付**：导入 Listener、消息校验、持久化文件恢复、单次导入事务、手动 ACK、聚焦测试和真实 RabbitMQ 端到端验收。
- **明确不做**：不实现对账消费者、并发抢占、处理租约、ACK 丢失恢复、消费重试队列或 DLQ；这些分别属于 Day 5 和 Day 6。

## 2. 消费流程

```text
RabbitMQ 投递 IMPORT_REQUESTED
  -> Listener 收到 JobRequestedMessage 与 deliveryTag
  -> Handler 校验消息公共字段
  -> ImportJobTransactionService.processPending(importJobId)
     -> 查询 PENDING import_job
     -> 查询 import_job_files
     -> 重新计算 SHA-256，并核对文件名、长度和哈希
     -> 条件更新 PENDING -> PROCESSING
     -> 复用 Week 3 解析、逐行校验、批量写入和终态统计
     -> 提交 SUCCESS / PARTIAL_SUCCESS / FAILED
  -> 事务代理正常返回
  -> channel.basicAck(deliveryTag, false)
```

消息只负责定位任务，数据库仍是业务真源。Listener 不读取 JWT，不接受 CSV 字节，也不信任消息提供业务统计。

## 3. 事务与 ACK

Day 4 采用一个数据库事务完成本次导入尝试：

```text
读取任务/文件
  + PENDING -> PROCESSING
  + 交易和行错误写入
  + 任务终态统计
  -> 一起提交或一起回滚
```

- 合法文件、部分错误文件和文件级业务错误均得到确定终态；事务提交后 ACK。
- 未预期数据库/运行时异常会回滚到消费前状态，Listener 不 ACK 并继续抛出异常。
- `basicAck` 只能写在事务方法成功返回之后，不能放在业务事务内部或 `finally` 中。
- Day 4 只验证一次失败投递不会产生错误业务结果或应用级 ACK，不把 Broker 的默认重新投递行为宣传成完整重试机制。
- Day 6 将用有限 retry queue 和 DLQ 替代持久故障下的无界重新投递风险。

## 4. 消息校验

只接受同时满足以下条件的消息：

- `messageId` 匹配 `outbox-<positive id>`；
- `eventType` 为 `IMPORT_REQUESTED`；
- `aggregateId` 为正数；
- `schemaVersion` 为 `1`；
- `createdAt` 非空。

非法消息不得调用导入 Service，也不得产生交易副作用。Day 4 让异常向 Listener Container 传播且不应用级 ACK；隔离、有限重试和 DLQ 归 Day 6。

## 5. 文件恢复和一致性检查

消费者按 `aggregateId` 查询 `import_jobs` 和 `import_job_files`，然后通过 `CsvImportFileParser.prepare` 对持久化字节重新执行请求级检查和 SHA-256 计算，并核对：

- 文件记录主键等于任务 ID；
- `content_length` 等于实际字节数；
- 实际字节数等于 `import_jobs.file_size_bytes`；
- 重算哈希等于 `import_jobs.file_hash`。

任一不一致均视为系统完整性错误并回滚，不伪造 `FAILED` 业务结果，也不修改 V7。

## 6. Listener Container

- 使用专用 `SimpleRabbitListenerContainerFactory`；
- `AcknowledgeMode.MANUAL`；
- Day 4 固定单消费者并发，默认 `concurrency=1`；
- `prefetch=1`，避免学习阶段一次占用多份最大 5 MiB 导入任务；
- 通过 `finguard.messaging.import-consumer.enabled` 控制自动启动；生产默认启用，普通测试默认关闭，真实消费者测试显式开启；
- 不在 Day 4 配置 `requeue=true`、重试次数、重试 TTL 或 DLX。

## 7. 测试与真实验收

自动化测试至少证明：

- 消息校验通过后才调用业务处理；非法类型、版本、ID 或 messageId 不调用业务处理；
- Listener 先完成 Handler，再 ACK；Handler 抛异常时不调用 `basicAck`；
- 持久化文件可恢复为相同内容，成功、部分成功和文件错误得到正确终态；
- 注入数据库写入异常时任务回到 `PENDING`，交易和行错误不残留；
- 真实 RabbitMQ 消息能被消费者确认，任务可从 HTTP `202/PENDING` 轮询到终态，MySQL 统计守恒。

验收结束必须清理任务、交易、行错误、文件、Outbox、队列消息和临时端口，并运行完整 `mvn clean test` 与 `git diff --check`。

## 8. 回滚

回退 Listener、Container 配置、导入事务入口和测试即可恢复 Day 3 行为。V7 不修改、不删除；回滚后任务仍能可靠受理和发布，但会停留在 `PENDING`。
