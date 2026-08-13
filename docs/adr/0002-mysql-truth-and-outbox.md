# ADR-0002：MySQL 作为事实源，并使用事务 Outbox

日期：2026-08-13

## 状态

Accepted

## 背景

CSV 导入和自动对账需要先持久化任务，再由 RabbitMQ 异步处理。若业务事务提交后直接发送消息，数据库成功而消息失败会丢任务；若先发消息再提交数据库，消费者可能读取不到任务。RabbitMQ 至少一次投递还会带来重复消费。

## 决策

MySQL 保存任务、业务结果、风险、审核和审计的权威状态。创建任务时在同一数据库事务写入 Outbox 事件；Relay 在提交后发布到 RabbitMQ，并用 Publisher Confirm 更新投递状态。消费者使用手动 ACK、任务行锁/终态判断和数据库唯一约束实现幂等，有限重试耗尽后进入 DLQ。

## 理由

- 同一事务写任务和 Outbox，关闭最危险的数据库/消息双写窗口。
- MySQL 约束和状态可以独立证明结果，即使消息重复或 Redis 不可用。
- 至少一次语义配合幂等比无法验证的 Exactly Once 声明更诚实、可测试。

## 后果

系统增加 Outbox 表、Relay、发布状态和清理责任；消息可能延迟或重复，消费者必须始终保持幂等。数据库成为可用性关键依赖，RabbitMQ 负责调度而不是业务事实。

## 替代方案

- **事务内直接发送 RabbitMQ**：外部 Broker 不参与本地数据库事务，仍存在提交顺序窗口。
- **只依赖 Broker 去重**：不能保护数据库重复写入或消费者崩溃重投。
- **分布式事务**：复杂度和运行成本不符合项目规模，也不能消除所有业务幂等需求。

## 证据

- [架构说明：一致性与可靠性](../ARCHITECTURE.md)
- [异步消息契约](../design/week4-day1-async-messaging-contract.md)
- [Outbox 设计](../design/week4-day3-outbox-publishing-design.md)
- [RabbitMQ 重试/DLQ 演练](../incidents/rabbitmq-retry-dlq.md)
