# FinGuard Core

FinGuard Core 是一个面向 Java 后端实习项目训练的交易导入、自动对账与异常审核平台。

当前仓库只完成阶段 0：建立可启动、可测试、可提交的最小 Spring Boot 工程基线。业务模块、数据库、中间件和安全功能尚未实现。

## 当前技术基线

- Java 17
- Maven 3.9+
- Spring Boot 3.5.16
- Spring MVC
- Spring Boot Actuator
- JUnit 5 / Spring Boot Test

## 本地运行

确认环境：

```powershell
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version
mvn -version
git --version
docker version
docker compose version
```

运行测试：

```powershell
mvn test
```

启动应用：

```powershell
mvn spring-boot:run
```

访问健康检查：

```text
GET http://localhost:8080/actuator/health
```

预期响应：

```json
{"status":"UP"}
```

## 当前范围

项目范围、学习方式和六周计划以 [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) 为唯一事实来源。后续每次只推进一个可运行、可测试、可提交的小里程碑。
