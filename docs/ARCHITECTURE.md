# FinGuard Core 架构说明

## 1. 系统定位与边界

FinGuard Core 是一个交易对账与风险审核平台：把账户、交易、CSV 异步导入、自动对账、风险命中、人工审核、审计和统计串成可运行闭环，并补齐容器、监控、部署、性能和安全验证。它不是银行核心系统，也不承诺生产 SLA、灾备等级或合规认证。

应用保持单体边界，以 MySQL 为业务事实真源；RabbitMQ 负责异步传递，Redis 只承载缓存和限流，Prometheus/Grafana 只负责观测。任何缓存或消息都不能替代数据库中的任务状态、幂等键和审计事实。

## 2. 运行架构

```mermaid
flowchart LR
    Client["API / Swagger 客户端"] -->|"HTTPS 或本机/SSH 隧道 HTTP"| App["Spring Boot 应用"]
    App -->|"事务、行锁、唯一约束"| MySQL[("MySQL 8.4")]
    App -->|"Outbox 发布 / 手动 ACK 消费"| MQ["RabbitMQ 4.3"]
    App -->|"统计缓存 / 固定窗口限流"| Redis[("Redis 8.2")]
    Prom["Prometheus"] -->|"抓取 /actuator/prometheus"| App
    Grafana["Grafana"] -->|"PromQL"| Prom
```

本地 Compose 可发布六个服务的回环端口；Linux Compose 不发布 MySQL、RabbitMQ、Redis 宿主机端口，应用、Prometheus、Grafana 只绑定 `127.0.0.1`。外部访问应通过 SSH 隧道或等价的受控入口。

## 3. 核心业务流

业务全貌见 [首页业务流程图](../README.md#业务流程)。以下时序图展开异步导入与审核并发的关键边界。API、Outbox Relay 和消费者是同一 Spring Boot 应用内的不同职责，图中拆开仅为说明交互。

### 3.1 异步导入与消息幂等

```mermaid
sequenceDiagram
    actor A as ADMIN
    participant API as 导入 API
    participant DB as MySQL
    participant Relay as Outbox Relay
    participant MQ as RabbitMQ
    participant Consumer as 导入消费者

    A->>API: 上传 CSV
    API->>API: 检查文件并计算 SHA-256
    API->>DB: 查询同账户、同哈希的已有任务
    alt 文件已受理
        DB-->>API: 返回已有任务
        API-->>A: 200 OK + 原任务 ID + duplicateFile
    else 新文件
        API->>DB: 同事务写任务、原文件、Outbox、受理审计
        DB-->>API: 提交成功
        API-->>A: 202 Accepted + 新任务 ID
        Note over API,Relay: HTTP 返回与后台 Relay 调度独立
        Relay->>DB: 查询到期的待发布事件
        DB-->>Relay: 返回 Outbox 事件
        Relay->>MQ: 发布 IMPORT_REQUESTED
        MQ-->>Relay: 发布确认且未被退回
        Relay->>DB: 标记 Outbox 为 SENT
        MQ->>Consumer: 投递导入消息
        Consumer->>DB: 开启事务，锁定任务并检查状态
        alt 任务已是终态
            DB-->>Consumer: 幂等返回，结束事务
        else 待处理或待恢复
            Consumer->>DB: 解析校验、写交易与行错误、更新终态
            DB-->>Consumer: 提交成功
        end
        Consumer->>MQ: 事务完成后手动 ACK
    end
    A->>API: GET 按 ID 查询任务及错误明细
    API->>DB: 读取持久化任务状态
    DB-->>API: 返回当前状态与计数
    API-->>A: 返回实际状态；未结束时继续轮询
```

新文件并发受理的最终去重由数据库唯一约束兜底；消息可能在发布后、Outbox 标记前被消费者收到。图中发布确认、标记与消费的顺序展示一种正常执行过程，不表示它们构成跨 MySQL / RabbitMQ 的原子事务。HTTP 202 只表示受理，任务终态为 `SUCCESS`、`PARTIAL_SUCCESS` 或 `FAILED`。

### 3.2 自动对账、风险识别与人工审核

```mermaid
sequenceDiagram
    actor A as ADMIN
    participant App as Spring Boot
    participant DB as MySQL
    participant MQ as RabbitMQ
    actor R as REVIEWER

    A->>App: 对已就绪的导入任务发起对账
    App->>DB: 校验成功或部分成功，且存在有效交易
    App->>DB: 同事务创建对账任务与 Outbox
    DB-->>App: 提交成功
    App-->>A: 202 Accepted + reconciliationJobId
    App->>MQ: 后台 Relay 发布 RECONCILIATION_REQUESTED
    MQ->>App: 对账消费者收到消息
    App->>DB: 开启事务，锁定任务并检查终态
    App->>DB: 读取导入交易与人工交易候选
    App->>App: 匹配交易，计算大额、疑似重复、高频风险
    App->>DB: 保存对账结果、风险命中与待审核任务
    App->>DB: 更新 COMPLETED，写对账审计并提交
    DB-->>App: 提交成功
    App->>MQ: 手动 ACK

    opt 生成了待审核任务
        R->>App: 查询审核任务及交易上下文
        App->>DB: 读取任务、version 与关联业务事实
        DB-->>App: 返回审核上下文
        App-->>R: 返回待审核任务及 version
        R->>App: 提交 CONFIRMED 或 IGNORED，并携带 version
        App->>DB: 查询当前任务状态与版本
        alt 已是终态，或版本已过期
            App-->>R: 409，拒绝重复决定或版本冲突
        else 状态与版本匹配
            App->>DB: 按 id、PENDING、version 条件更新
            alt 更新成功
                App->>DB: 同事务写审核审计并提交
                DB-->>App: 提交成功
                App-->>R: 返回审核终态与递增后的版本
            else 并发请求已抢先更新
                App->>DB: 回滚当前事务
                App-->>R: 409 版本冲突
            end
        end
    end
```

导入就绪后仍需 ADMIN 主动发起对账。`UNMATCHED`、`DUPLICATE`、`SUSPICIOUS` 对账结果与风险命中分别生成审核任务；同一交易可能关联多项待审核任务。审核只更新审核结论，不改写原始交易和对账结果。上述业务变更通过提交后事件使统计缓存失效，读取统计时按需回源 MySQL。

## 4. 一致性与可靠性

- HTTP 写操作由 Service 事务包围；外部解析、密码计算和 JWT 签发不无理由占用数据库事务。
- 导入与对账受理采用 Transactional Outbox，业务任务和待发布事件同事务提交。
- 消费者使用任务行锁、终态检查、唯一约束和提交后 ACK，应对重复投递、并发消费和 ACK 丢失。
- 失败分为业务失败、可重试系统失败和非法消息；系统失败最多经过 5 秒、30 秒两级延迟，耗尽后进入独立 DLQ。
- 审核决策采用 `id + PENDING + version` 条件更新，区分不存在、终态重复和版本冲突。
- Redis 故障时统计回源 MySQL、限流 fail-open；这保住可用性，但会暂时削弱登录/上传频控，健康检查仍暴露依赖异常。

## 5. 安全边界

- 登录使用 BCrypt，签发两小时 HS256 JWT；密钥必须由运行环境提供，Base64 解码后至少 32 字节。
- Spring Security 无状态校验 Bearer Token；ADMIN 负责业务写入和审计查询，REVIEWER 负责业务读取与审核决策。
- 匿名仅可访问登录、健康、Prometheus 和 OpenAPI/Swagger；Actuator 只暴露 `health,prometheus`。
- CSV 受 5 MiB、10,000 行、严格 UTF-8、RFC 4180 与字段/领域校验约束。
- 审计摘要、消息头和指标标签使用固定低基数值，不记录密码、JWT、CSV 原文、SQL、堆栈或自由文本异常。

## 6. 可观测性

应用暴露 HTTP/JVM/HikariCP 默认指标和三组业务指标：导入终态 Counter、对账处理 Timer、消费者失败 Counter。Prometheus 保存时间序列，Grafana 自动装载数据源和 9 个面板。业务 ID、用户名、文件名和异常文本不得进入标签。

## 7. 代码组织

`src/main/java/com/finguard/core` 按账户、交易、认证、导入、对账、消息、风险、审核、审计、统计、限流和观测拆包。Controller 负责协议与可信身份提取，Service 负责事务和业务规则，Mapper 负责参数化 SQL，Flyway 负责不可变模式演进。

相关文档：[数据库](DATABASE.md)、[API](API.md)、[部署](DEPLOYMENT.md)、[监控](MONITORING.md)。
