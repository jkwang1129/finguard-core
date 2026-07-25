# FinGuard Core 任务清单

## 阶段 0：工程基线

- [x] 检查当前目录和已有文件
- [x] 确认 JDK 17 可用
- [x] 确认 Maven 可用
- [x] 确认 Git 可用
- [x] 确认 Docker 与 Docker Compose 可用（Docker Desktop 4.82.0、Engine 29.6.1、Compose v5.3.0，使用 WSL2 后端）
- [x] 初始化 Git 仓库
- [x] 创建最小 Spring Boot 项目
- [x] 验证应用能够启动
- [x] 验证 `/actuator/health` 返回 `UP`
- [x] 运行并通过第一条测试
- [x] 建立 `README.md` 和 `TASKS.md`
- [x] 完成第一次 Git 提交

## 第 1 周任务清单

目标：完成工程基线、数据库设计，以及账户与交易的最小 CRUD；学习 Maven、Spring MVC、分层、MySQL 和 REST。

### Day 1：环境与最小骨架

- 完成阶段 0 全部验收项
- 理解 Maven 项目结构、依赖管理和常用生命周期
- 理解 Spring Boot 启动流程与 Actuator 健康检查
- 保留一次清晰的初始 Git 提交

### Day 2：需求与数据模型

- [x] 明确账户、交易的最小业务规则和验收标准
- [x] 绘制第一版 ER 图
- [x] 设计账户表、交易表及必要唯一约束
- [x] 完成设计评审，未提前生成业务表
- [x] 提交 Day 2 设计文档
- 设计文档：[`docs/design/day2-requirements-and-data-model.md`](docs/design/day2-requirements-and-data-model.md)

### Day 3：MySQL 与迁移基线

- [x] 用 Docker Compose 启动 MySQL
- [x] 配置数据源和 MyBatis-Plus
- [x] 引入 Flyway 并创建首个最小迁移
- [x] 验证应用能连接数据库

### Day 4：账户 CRUD

- [x] 讲清 Controller、Service、Mapper 以及 DTO、Entity 的职责边界
- [x] 建立账户模块骨架和创建、查询、改名、状态切换、软删除接口
- [x] 完成账户 Service 核心业务逻辑并逐项解释实现过程
- [x] 完成输入标准化、参数校验、重复编号预检查和数据库唯一约束兜底
- [x] 完成 15 个 Service 单元测试和 6 个 Controller 参数校验测试
- [x] 运行 `mvn clean test`，26 个测试全部通过且无跳过项
- [x] 在真实 MySQL 上走通创建、查询、改名、禁用和软删除流程
- [x] 验证活动账户不能删除、软删除账户默认查不到且编号不能复用
- [x] 将统一错误响应以及 404/409 状态映射按计划留到 Day 6

### Day 5：交易 CRUD

- [x] 明确金额使用 `BigDecimal` 的精度与舍入约束
- [x] 讲清交易时间、业务流水号、`MANUAL` 来源和数据库唯一约束
- [x] 建立交易 Entity、枚举、DTO、VO、Mapper 和异常骨架
- [x] 完成交易 Service 核心业务逻辑并逐项解释实现过程
- [x] 完成创建、查询、修改和删除的最小闭环
- [x] 完成 28 个 Service 单元测试和 12 个 Controller 参数校验测试
- [x] 运行 `mvn clean test`，66 个测试全部通过且无跳过项
- [x] 在真实 MySQL 上走通创建、查询、修改、禁用账户查询和软删除流程
- [x] 验证禁用账户不能创建或删除交易、重复删除幂等成功
- [x] 验证软删除交易默认查不到、原流水号不能复用且失败请求未写入数据库

### Day 6：查询与错误处理

- [x] 完成账户和交易的基础分页、稳定排序与组合条件查询
- [x] 建立统一错误响应与全局异常处理，完成 `400`、`404`、`409`、`500` 映射
- [x] 补充 Controller、Service 和真实 MySQL 集成测试
- [x] 运行 `mvn clean test`，86 个测试全部通过且无跳过项
- [x] 真实启动应用并验证分页查询、参数错误、资源不存在、重复数据和非法状态操作
- [x] 精确清理 HTTP 验收数据并确认 8080 端口释放

### Day 7：周验收与复盘

- [x] 从空环境实际运行本周流程
- [x] 整理学习笔记、常见错误和面试问题
- 复盘文档：[`docs/review/week1-review.md`](docs/review/week1-review.md)
- [x] 检查 Git 历史是否连续且每次提交可解释
- [x] 只在本周目标稳定后进入第 2 周

## 今天的最小任务

- [x] 读完并锁定 `PROJECT_BRIEF.md` 的阶段 0 范围
- [x] 检查 JDK、Maven、Git、Docker
- [x] 创建最小 Spring Boot + Actuator 骨架
- [x] 让第一条健康检查集成测试通过
- [x] 实际启动应用并确认健康响应
- [x] 完成第一次 Git 提交
