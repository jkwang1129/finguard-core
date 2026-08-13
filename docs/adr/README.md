# FinGuard Core 架构决策记录

Architecture Decision Record（ADR）记录已经影响代码、数据或运行方式的重要选择。它回答“为什么这样设计”，详细实现仍以 [架构说明](../ARCHITECTURE.md)、`docs/design/` 和源码为准。

## 状态约定

- `Proposed`：已提出但尚未采用。
- `Accepted`：已采用并由当前实现或证据支持。
- `Deprecated`：不再建议用于新增实现，但可能仍存在。
- `Superseded`：已被后续 ADR 明确替代。

已接受的 ADR 不直接改写历史语义。若决策发生重大反转，应新增编号并在新旧记录中标明替代关系。

## 索引

| 编号 | 决策 | 状态 |
| --- | --- | --- |
| [0001](0001-modular-monolith.md) | 使用模块化单体 | Accepted |
| [0002](0002-mysql-truth-and-outbox.md) | MySQL 作为事实源，并使用事务 Outbox | Accepted |
| [0003](0003-rbac-duty-separation.md) | ADMIN 与 REVIEWER 职责分离 | Accepted |
| [0004](0004-redis-fail-open.md) | Redis 缓存回源与限流 fail-open | Accepted |
| [0005](0005-immutable-image-deployment.md) | 完整 Git SHA 不可变镜像部署 | Accepted |

## 新增规则

新 ADR 使用四位递增编号，至少包含状态、背景、决策、理由、后果、替代方案和证据。记录只描述本项目实际采用或明确提出的选择，不以流行技术代替问题证据。
