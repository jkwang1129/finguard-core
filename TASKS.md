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

- 用 Docker Compose 启动 MySQL
- 配置数据源和 MyBatis-Plus
- 引入 Flyway 并创建首个最小迁移
- 验证应用能连接数据库

### Day 4：账户 CRUD

- 先讲清 Controller、Service、Mapper 的职责边界
- 由用户编写账户 Service 核心业务逻辑
- 完成参数校验、正常路径和异常路径测试

### Day 5：交易 CRUD

- 明确金额使用 `BigDecimal` 的精度与舍入约束
- 由用户编写交易 Service 核心业务逻辑
- 完成创建、查询、修改和删除的最小闭环

### Day 6：查询与错误处理

- 完成基础分页和条件查询
- 建立统一错误响应与全局异常处理
- 补充单元测试和集成测试

### Day 7：周验收与复盘

- 从空环境实际运行本周流程
- 整理学习笔记、常见错误和面试问题
- 检查 Git 历史是否连续且每次提交可解释
- 只在本周目标稳定后进入第 2 周

## 今天的最小任务

- [x] 读完并锁定 `PROJECT_BRIEF.md` 的阶段 0 范围
- [x] 检查 JDK、Maven、Git、Docker
- [x] 创建最小 Spring Boot + Actuator 骨架
- [x] 让第一条健康检查集成测试通过
- [x] 实际启动应用并确认健康响应
- [x] 完成第一次 Git 提交
