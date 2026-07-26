# Week 2 Day 6：事务边界、索引与 EXPLAIN

## 1. 目标与范围

本日目标不是增加新的业务接口，而是验证当前认证、账户和交易模块的数据一致性与查询效率：

- 明确 Service 层的读写事务边界；
- 用真实 MySQL 微实验验证用户和角色关系的多表写入会在异常时整体回滚；
- 还原认证与交易分页的真实 SQL，并使用代表性数据执行 `EXPLAIN`；
- 只在执行计划提供证据时，通过新的 Flyway 迁移增加必要索引。

本日不修改 Controller，不进入 CSV 导入、Redis 或 RabbitMQ。

## 2. 事务边界审计

| 入口方法 | 主要数据库操作 | 事务结论 | 设计理由 |
|---|---|---|---|
| `AccountServiceImpl.create` | 账户号查重、插入账户、回读账户 | 写事务 | 检查与写入共同组成一次账户创建操作，数据库唯一约束负责并发兜底 |
| `AccountServiceImpl.getById` | 查询单个账户 | `readOnly` | 只读查询，明确事务意图 |
| `AccountServiceImpl.query` | 条件查询、总数统计、分页查询 | `readOnly` | 总数与当前页属于同一个分页读取入口 |
| `AccountServiceImpl.updateName` | 读取账户、更新名称、回读账户 | 写事务 | 读取、状态判断和更新属于一个业务操作 |
| `AccountServiceImpl.updateStatus` | 读取账户、修改状态、回读账户 | 写事务 | 状态校验与更新必须处于同一写事务 |
| `AccountServiceImpl.delete` | 读取账户、检查历史交易、软删除账户 | 写事务 | 多次检查与软删除需要作为一个业务操作提交或回滚 |
| `TransactionServiceImpl.create` | 读取账户、业务键查重、插入交易、回读交易 | 写事务 | 账户状态检查、查重和写入共同决定创建结果 |
| `TransactionServiceImpl.getById` | 查询单个交易 | `readOnly` | 只读查询，明确事务意图 |
| `TransactionServiceImpl.query` | 条件查询、总数统计、分页查询 | `readOnly` | 总数与当前页属于同一个分页读取入口 |
| `TransactionServiceImpl.update` | 读取交易、校验来源、更新、回读 | 写事务 | 校验和写入不能被拆成互不相关的操作 |
| `TransactionServiceImpl.delete` | 读取交易和账户、校验状态、软删除交易 | 写事务 | 多表读取共同决定能否删除 |
| `AuthBootstrapRunner.run` | 查询角色、插入用户、绑定用户角色 | 写事务 | 用户与角色关系必须一起成功或一起回滚 |
| `DatabaseUserDetailsService.loadUserByUsername` | 按用户名读取用户，再读取角色列表 | `readOnly` | 两次认证查询形成一个短读取边界，避免把认证之外的工作纳入事务 |
| `AuthServiceImpl.login` | 调用认证管理器、BCrypt 校验、签发 JWT | 不额外扩大事务 | 数据库读取由 `UserDetailsService` 的短事务负责；密码计算和 JWT 签发不占用数据库事务 |

## 3. Spring 事务知识与风险

### 3.1 事务代理

Spring 通常通过代理拦截从 Bean 外部进入的公开方法。代理在方法调用前开启事务，在正常返回时提交，在符合回滚规则的异常抛出时回滚。

### 3.2 自调用失效

同一个对象内部使用 `this.someTransactionalMethod()` 调用另一个带 `@Transactional` 的方法时，请求没有经过 Spring 代理，因此被调用方法上新声明的事务传播、只读或回滚规则不会单独生效。事务入口应放在由其他 Bean 调用的公开 Service 方法上。

### 3.3 异常回滚规则

默认情况下，Spring 对 `RuntimeException` 和 `Error` 回滚，对受检异常不自动回滚。业务确实要求受检异常回滚时，应显式使用 `rollbackFor`，不能只凭“方法抛了异常”推断数据库一定回滚。

### 3.4 `readOnly` 的含义

`@Transactional(readOnly = true)` 表达只读意图，并可让事务管理器或数据库驱动进行相应优化；它不是跨数据库一致的强制“禁止写入”安全机制。业务正确性仍依赖代码边界、权限和数据库约束。

### 3.5 事务范围过大

事务持有时间越长，数据库连接、行锁和一致性视图占用时间越长。远程调用、文件解析、长时间计算、BCrypt 校验和 JWT 签发不应无理由包进数据库事务。本项目把认证数据读取限制在 `UserDetailsService`，不把整个登录流程变成长事务。

## 4. 多表回滚微实验

### 4.1 实验设计

`TransactionBoundaryIntegrationTest` 定义了一个只存在于测试上下文中的 Spring Bean。测试先确认该 Bean 是 AOP 代理，再从 Bean 外部调用带 `@Transactional` 的公开方法：

1. 插入用户名为 `day6-tx-test-rollback` 的用户；
2. 查询 `ADMIN` 角色；
3. 插入 `user_roles` 绑定；
4. 主动抛出 `IllegalStateException`；
5. 在事务方法退出后，使用独立查询检查两张表。

没有为了演示事务增加生产 Controller 或业务接口。

### 4.2 实验结果

聚焦测试执行：

```text
mvn "-Dtest=TransactionBoundaryIntegrationTest,
DatabaseUserDetailsServiceTest,AuthLoginIntegrationTest" test
```

结果为 9 项测试全部通过，无失败、错误或跳过项。异常抛出后：

```text
users(day6-tx-test-rollback) = 0
user_roles(day6-tx-test-rollback) = 0
```

这证明运行时异常跨过 Spring 事务代理返回给调用者时，已经成功执行的用户插入和角色绑定会一起回滚，而不是只回滚最后一条 SQL。

## 5. 真实查询与 EXPLAIN

### 5.1 数据与方法

执行计划使用真实 MySQL 8.4.10。为了避免在空表或极小表上误判优化器选择，临时创建了：

- 10 个 `DAY6_EXPLAIN_` 前缀账户；
- 2,000 条交易，其中 1,800 条未删除、200 条软删除；
- 1 个认证用户和 `ADMIN`、`REVIEWER` 两个角色绑定。

插入完成后执行 `ANALYZE TABLE` 更新统计信息。分析结束后，交易、账户、用户和角色绑定临时数据均已按专用前缀清理为零。

### 5.2 用户名查询

MyBatis 实际 SQL：

```sql
SELECT id, username, password_hash, status
FROM users
WHERE username = ?;
```

修改前执行计划：

| type | key | rows | Extra |
|---|---|---:|---|
| `const` | `uk_users_username` | 1 | 无 |

结论：唯一索引已准确支持规范化用户名等值查询，不增加普通 `username` 索引。

### 5.3 用户角色查询

MyBatis 实际 SQL：

```sql
SELECT r.role_code
FROM roles r
INNER JOIN user_roles ur ON ur.role_id = r.id
WHERE ur.user_id = ?
ORDER BY r.role_code;
```

执行计划使用：

- `uk_roles_role_code` 扫描两个固定角色，`Using index`；
- `user_roles` 主键 `(user_id, role_id)`，连接类型为 `eq_ref`，`Using index`；
- 用户名子查询继续使用 `uk_users_username`。

`(user_id, role_id)` 的最左列就是当前查询条件 `user_id`，因此不增加重复的 `user_id` 单列索引。现有 `idx_user_roles_role_id` 保留，用于从角色反向查找用户和支持外键相关访问。

### 5.4 交易分页真实 SQL

MyBatis-Plus 对按账户分页实际执行两条 SQL。

总数查询：

```sql
SELECT COUNT(*) AS total
FROM transactions
WHERE deleted = 0
  AND account_id = ?;
```

数据页查询：

```sql
SELECT id, account_id, external_transaction_no, direction, amount,
       transaction_time, description, source, created_at, updated_at, deleted
FROM transactions
WHERE deleted = 0
  AND account_id = ?
ORDER BY transaction_time DESC, id DESC
LIMIT ?;
```

组合筛选时，`direction`、`source`、`external_transaction_no` 和时间范围会继续追加到同一个 `WHERE` 中。

### 5.5 修改前执行计划

| 查询 | type | key | rows | Extra |
|---|---|---|---:|---|
| 默认总数 `deleted = 0` | `ALL` | 无 | 2,000 | `Using where` |
| 默认数据页 | `ALL` | 无 | 2,000 | `Using where; Using filesort` |
| 按账户总数 | `ref` | 业务唯一索引 | 200 | `Using where` |
| 按账户数据页 | `ref` | `idx_transactions_account_time` | 200 | `Using where; Backward index scan` |
| 按账户和时间范围数据页 | `ref` | `idx_transactions_account_time` | 200 | `Using where; Backward index scan` |

结论：

- 现有 `(account_id, transaction_time)` 能支持按账户过滤和时间倒序；InnoDB 二级索引记录还携带主键值，因此稳定的 `id DESC` 没有触发额外文件排序；
- 默认无过滤分页没有以 `deleted` 开头且匹配排序的索引，出现全表扫描和 `Using filesort`；
- 这构成新增一个默认分页索引的直接证据。

### 5.6 修改后执行计划

新增：

```sql
CREATE INDEX idx_transactions_deleted_time_id
    ON transactions (deleted, transaction_time DESC, id DESC);
```

再次执行 `ANALYZE TABLE` 后：

| 查询 | type | key | rows | Extra |
|---|---|---|---:|---|
| 默认总数 `deleted = 0` | `ref` | `idx_transactions_deleted_time_id` | 1,800 | `Using index` |
| 默认数据页 | `ref` | `idx_transactions_deleted_time_id` | 1,800 | 无 `Using filesort` |
| 按账户数据页 | `ref` | `idx_transactions_account_time` | 200 | `Using where; Backward index scan` |

新索引的主要收益不是把低区分度的 `deleted` 变成高选择性条件，而是让默认分页可以按目标顺序读取并在取得 20 行后停止，同时让总数查询扫描更窄的覆盖索引。按账户查询仍选择原索引，说明两个索引服务于不同的主要访问路径。

## 6. 索引决策

### 6.1 最左前缀

联合索引按照从左到右的列顺序组织。`user_roles(user_id, role_id)` 可以直接支持 `WHERE user_id = ?`；只有 `role_id` 条件时不能把它当成以 `role_id` 开头的索引，因此项目另有反向索引 `idx_user_roles_role_id`。

### 6.2 回表与覆盖索引

查询所需列全部位于索引中时，可以直接从索引返回，`EXPLAIN` 常显示 `Using index`。例如默认交易总数只需要判断 `deleted` 并计数，新索引可以覆盖它。

默认交易数据页需要金额、描述、来源等不在分页索引中的列，因此找到目标主键后仍需要回表；但 `LIMIT 20` 使回表范围保持很小。为了追求完全覆盖而把所有业务列塞入索引，会显著放大索引，不值得。

### 6.3 前缀模糊匹配

`LIKE 'abc%'` 已知字符串左边界，B-Tree 可以定位以 `abc` 开头的范围。`LIKE '%abc%'` 的开头未知，无法从 B-Tree 的有序前缀确定起点，通常需要扫描。账户关键字查询同时搜索账户号和名称，因此不能仅凭“字段会被搜索”就给两个字段盲目堆索引。

### 6.4 最终决定

- 保留 `uk_users_username`，不新增用户名索引；
- 保留 `user_roles(user_id, role_id)` 和 `idx_user_roles_role_id`，不新增重复索引；
- 保留 `idx_transactions_account_time` 服务按账户分页；
- 通过 `V3__add_transaction_pagination_index.sql` 新增 `idx_transactions_deleted_time_id`，服务默认交易分页；
- 不修改已经执行的 `V1`、`V2`。

## 7. 常见错误

- 在同一个类中自调用带 `@Transactional` 的方法，并误以为新事务规则生效；
- 捕获异常后不再抛出，导致事务代理看到正常返回并提交；
- 认为所有受检异常默认都会回滚；
- 把密码计算、网络请求或大文件解析放进长事务；
- 在只有几行数据时看到全表扫描，就直接认定索引无效；
- 只看 `possible_keys`，不检查优化器实际选择的 `key`；
- 为每个查询字段都创建单列索引，忽略写入成本和联合索引；
- 修改已执行的 Flyway 迁移，造成校验和不一致；
- 为了消除回表，把整行所有列都加入覆盖索引。

## 8. 面试问答

### 为什么事务放在 Service 层？

Service 方法表达完整业务操作，通常会组合多次查询、校验和写入。Controller 只处理 HTTP 契约，Mapper 只处理单条数据访问，都不适合作为业务原子性的主要边界。

### Spring 事务为什么会自调用失效？

事务由 Spring 代理在方法调用边界拦截。同一对象内部的 `this.method()` 不经过代理，所以被调用方法上新声明的事务属性不会被拦截应用。

### 为什么登录方法本身没有包一个大事务？

真正需要一致读取的是用户和角色，已在 `DatabaseUserDetailsService` 中使用短只读事务。BCrypt 校验和 JWT 签发不需要数据库事务，把它们放进去只会延长连接占用时间。

### 为什么 `deleted` 区分度低仍能放在分页索引首列？

该索引的核心目标不是依靠 `deleted` 大幅减少扫描比例，而是固定读取未删除记录后，直接按照 `transaction_time DESC, id DESC` 获取前 20 行并避免文件排序。总数查询也能使用更窄的覆盖索引。

### 为什么没有给所有筛选字段都加索引？

当前证据只显示默认分页存在明确缺口。额外索引会占空间并增加交易写入、更新和删除成本；没有稳定业务频率和执行计划证据时，不应提前堆索引。

## 9. 验收记录

- 聚焦事务与认证回归：9 项测试通过；
- V3 迁移与索引基线：5 项测试通过；
- `mvn clean test`：157 项测试通过，0 失败、0 错误、0 跳过；
- 首次完整测试发现认证数据库测试仍固定断言 Flyway 版本 2，更新为版本 3 后完整重跑通过；
- 独立临时空库从 `V1`、`V2` 迁移到 `V3`，数据库、认证、索引与事务回滚 11 项测试通过，临时库随后删除；
- 真实应用 `/actuator/health` 返回 `UP`，匿名访问交易分页返回预期 `401`；
- 当前真实库 Flyway 版本为 3，分页索引列顺序为 `deleted ASC, transaction_time DESC, id DESC`；
- 验收结束后 `accounts=0`、`transactions=0`、`users=0`、`user_roles=0`、固定 `roles=2`；
- Day 6 与 Day 2 测试用户残留均为 0，8080 端口已释放；
- Docker MySQL 状态为 `healthy`，`git diff --check` 通过。
