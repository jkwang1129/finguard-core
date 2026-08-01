# Week 4 Day 2：RabbitMQ 基础设施与基础拓扑

## 1. 状态与交付结论

- **状态**：已完成
- **目标**：让 FinGuard Core 可以连接真实 RabbitMQ，并自动声明 Week 4 异步导入与对账所需的持久化基础拓扑。
- **交付结论**：RabbitMQ 依赖、Docker 服务、连接配置、两个业务 exchange、两个主队列和两个 binding 已完成；真实 RabbitMQ 路由测试、完整 Maven 回归和临时 HTTP 健康检查均通过。

本日只建立消息基础设施，不改变 Week 3 的同步导入、同步对账、HTTP 状态语义或数据库结构。

## 2. 拓扑设计

本日使用 `direct` exchange，routing key 必须精确匹配，避免在消费者尚未实现时引入通配路由。

| 用途 | Exchange | Routing key | 主队列 | 持久化 |
|---|---|---|---|---|
| 异步导入 | `finguard.import.exchange` | `import.requested` | `finguard.import.queue` | exchange、queue、binding 均持久化 |
| 异步对账 | `finguard.reconciliation.exchange` | `reconciliation.requested` | `finguard.reconciliation.queue` | exchange、queue、binding 均持久化 |

重试队列、死信队列、Publisher Confirm、消费者 ACK 和业务消费者留给后续 Day 3～Day 6，不在本日提前声明或实现。

## 3. 配置与文件

- `pom.xml`：增加 `spring-boot-starter-amqp`。
- `docker-compose.yml`：增加 RabbitMQ 3.13 management 服务、持久化 volume、5672/15672 端口和 `rabbitmq-diagnostics ping` 健康检查。
- `.env.example`：记录 RabbitMQ 主机、端口、虚拟主机、用户名和密码变量；真实 `.env` 仍被 Git 忽略。
- `src/main/resources/application.yml`：从环境变量读取 RabbitMQ 连接信息，不把凭据写入源码。
- `src/main/java/com/finguard/core/messaging/config/RabbitMessagingConfiguration.java`：集中声明 Day 2 基础拓扑。
- `src/test/java/com/finguard/core/messaging/RabbitMessagingTopologyIntegrationTest.java`：验证队列存在以及两条 routing key 能路由到对应队列。

## 4. 验收矩阵

- RabbitMQ 容器为 `healthy`，MySQL 容器保持 `healthy`。
- Spring Boot 聚焦拓扑测试 `2/2` 通过。
- 完整 `mvn clean test`：`232/232` 通过，失败 `0`，错误 `0`，跳过 `0`。
- RabbitMQ 实际检查确认两个 direct durable exchange、两个 durable 主队列和两个业务 binding 存在。
- FinGuard 使用临时端口 `18080` 启动后，`GET /actuator/health` 返回 `HTTP 200` 和 `UP`；测试后 18080 已释放。
- 8080 当前由工作区外的 `cangqiong-server` 占用，未停止或修改该进程。
- 测试消息已消费，业务队列消息数为 `0`。
- `git diff --check` 和范围审计在提交前执行。

## 5. 边界与回滚

本日没有新增 Flyway 迁移、Controller、Service、生产者、消费者、Outbox、文件表、Redis、重试、DLQ、自动对账或业务消息 payload。

若需要回滚，只回退本日新增的 AMQP 依赖、RabbitMQ Compose 服务、连接配置、拓扑配置、聚焦测试和文档；不修改 V1～V6，也不删除 RabbitMQ 数据卷中的环境数据。

## 6. 学习重点

- Spring Boot 如何通过 AMQP 自动创建连接工厂、RabbitAdmin 和 RabbitTemplate。
- Exchange、Queue、Binding、Routing Key 的职责边界。
- durable topology 与后续 persistent message 的区别。
- 为什么 Day 2 先建立基础设施，Day 3 再实现任务、原始文件和 Outbox 的原子提交。
