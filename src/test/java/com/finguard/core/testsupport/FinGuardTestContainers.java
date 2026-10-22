package com.finguard.core.testsupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试的中间件来源：用 Testcontainers 拉起 MySQL / RabbitMQ / Redis。
 *
 * <p>这样 {@code mvn test} 不再依赖本机 localhost 上预先跑着的中间件，
 * 干净 clone 的仓库也能一条命令跑完全部集成测试。
 *
 * <p><b>单一真源（本类存在的核心理由）：</b>
 * 容器里创建出来的账号密码，与注入给 Spring 的
 * {@code spring.datasource.*} / {@code spring.rabbitmq.*} / {@code spring.data.redis.*}
 * 必须来自同一组常量。历史上这两处各写各的，容器用 A 密码建账号、Spring 用 B 密码去连，
 * 直接报 {@code ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN}，
 * 而且看起来像是「中间件坏了」。现在两处都引用下面的常量，结构上不可能再错位。
 *
 * <p><b>镜像与启动参数对齐 {@code docker-compose.yml}</b>，避免出现
 * 「本机 compose 能过、容器里挂」的字符集 / 时区差异。
 *
 * <p><b>如何临时退回本机中间件（仅用于排查对比）：</b>
 * 加 {@code -Dfinguard.testcontainers.enabled=false}，此时本类不会启动任何容器，
 * 测试回落到 {@code application.yml} + {@code .env} 里的 localhost 配置。
 */
public final class FinGuardTestContainers {

    /** 关掉 Testcontainers 的系统属性名，见类注释。 */
    static final String ENABLED_PROPERTY = "finguard.testcontainers.enabled";

    // ---------------------------------------------------------------- 镜像
    // 与 docker-compose.yml 中的 image 保持完全一致，防止「本地能过、CI 挂」。
    private static final String MYSQL_IMAGE = "mysql:8.4.10";
    private static final String RABBITMQ_IMAGE = "rabbitmq:4.3.4-management";
    private static final String REDIS_IMAGE = "redis:8.2.8-alpine";

    // ------------------------------------------- 凭据（唯一真源，改一处即全部生效）
    private static final String MYSQL_DATABASE = "finguard";
    private static final String MYSQL_USERNAME = "finguard";
    private static final String MYSQL_PASSWORD = "finguard-test";

    private static final String RABBITMQ_USERNAME = "finguard";
    private static final String RABBITMQ_PASSWORD = "finguard-test";
    private static final String RABBITMQ_VHOST = "/";

    private static final String REDIS_PASSWORD = "finguard-test";
    private static final int REDIS_PORT = 6379;

    private FinGuardTestContainers() {
    }

    /** Testcontainers 是否启用（默认启用）。 */
    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    /**
     * 返回需要覆盖到 Spring {@code Environment} 的属性。
     *
     * <p>调用方见 {@link TestcontainersEnvironmentPostProcessor}，
     * 它以最高优先级 {@code addFirst} 进去，从而压过 {@code .env} 与本机地址。
     *
     * @return 容器连接属性；未启用时返回空表，让 {@code .env} 的 localhost 配置生效
     */
    public static Map<String, Object> springProperties() {
        if (!enabled()) {
            info("已通过 -D" + ENABLED_PROPERTY + "=false 关闭，集成测试将连接 .env 中配置的本机中间件");
            return Map.of();
        }
        return Containers.SPRING_PROPERTIES;
    }

    /**
     * 这里刻意用 {@code System.out} 而不是 SLF4J。
     *
     * <p>本类是在 {@code EnvironmentPostProcessor} 阶段被触发的，那时日志系统还没初始化，
     * SLF4J 仍绑定在 NOP 实现上——写 {@code log.info(...)} 会被静默丢弃，
     * 排查容器问题时很容易误以为「这段代码没执行」。
     */
    private static void info(String message) {
        System.out.println("[testcontainers] " + message);
    }

    /**
     * 容器持有者：用嵌套类做延迟初始化——只有真正需要时才在静态块里启动容器，
     * 关闭 Testcontainers 时这个类根本不会被加载，Docker 一次都不会被调用。
     */
    private static final class Containers {

        private static final MySQLContainer<?> MYSQL = createMysql();

        private static final RabbitMQContainer RABBITMQ = createRabbitMq();

        private static final GenericContainer<?> REDIS = createRedis();

        private static final Map<String, Object> SPRING_PROPERTIES = startAll();

        private Containers() {
        }

        private static MySQLContainer<?> createMysql() {
            return new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                    .withDatabaseName(MYSQL_DATABASE)
                    .withUsername(MYSQL_USERNAME)
                    .withPassword(MYSQL_PASSWORD)
                    // 对齐 docker-compose.yml 的 mysql.command：
                    // 少了 default-time-zone，按时间聚合的断言会因时区漂移而随机挂。
                    .withCommand(
                            "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_0900_ai_ci",
                            "--default-time-zone=+08:00",
                            // Testcontainers 的 MySQL 默认把 max_allowed_packet 压到 1MB，
                            // 导入类测试写大字段会直接 PacketTooBigException，这里抬到 64M。
                            "--max-allowed-packet=64M"
                    )
                    .withEnv("TZ", "Asia/Shanghai")
                    // MySQL 8.4 默认 caching_sha2_password：容器走 TCP 而非本机 socket，
                    // 必须显式放开公钥检索，同时关掉 SSL（本地容器没有证书）。
                    .withUrlParam("sslMode", "DISABLED")
                    .withUrlParam("allowPublicKeyRetrieval", "true")
                    .withUrlParam("connectionTimeZone", "Asia/Shanghai");
        }

        private static RabbitMQContainer createRabbitMq() {
            // 【踩坑记录】必须用 withAdminUser / withAdminPassword，不要用 withUser(u, p)，
            // 也不要试图用 withEnv("RABBITMQ_DEFAULT_USER", ...)。
            //
            // 反编译测试容器 1.21.4 可以看到：
            //   1) configure()（容器启动时执行）会把 adminUsername / adminPassword 两个私有字段
            //      写进 RABBITMQ_DEFAULT_USER / RABBITMQ_DEFAULT_PASS —— 也就是说 withEnv 设的同名
            //      环境变量会在启动瞬间被这两个字段覆盖掉，白设；
            //   2) withUser(u, p) 既不碰这两个字段，也不设环境变量，它只是把
            //      `rabbitmqadmin declare user name=.. password=..` 排进「启动后执行」的队列，
            //      而镜像里并没有 rabbitmqadmin，这条声明实际不会生效。
            //
            // 结果就是：容器稳稳地以默认 guest/guest 跑起来，而 Spring 拿着我们配的账号去连，
            // 报出来的却是 ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN，
            // 看起来像中间件崩了，其实账号压根没建。
            return new RabbitMQContainer(DockerImageName.parse(RABBITMQ_IMAGE))
                    .withAdminUser(RABBITMQ_USERNAME)
                    .withAdminPassword(RABBITMQ_PASSWORD)
                    .withEnv("TZ", "Asia/Shanghai");
        }

        private static GenericContainer<?> createRedis() {
            return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT)
                    .withEnv("TZ", "Asia/Shanghai")
                    .withCommand("redis-server", "--appendonly", "yes", "--requirepass", REDIS_PASSWORD)
                    // 带 requirepass 时端口一开就能连上，但 PING 仍可能被拒；
                    // 等日志里出现 Ready to accept connections 最稳。
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
        }

        private static Map<String, Object> startAll() {
            info("启动容器：MySQL " + MYSQL_IMAGE + " / RabbitMQ " + RABBITMQ_IMAGE + " / Redis " + REDIS_IMAGE);

            List<Startable> containers = new ArrayList<>();
            containers.add(MYSQL);
            containers.add(RABBITMQ);
            containers.add(REDIS);
            Startables.deepStart(containers.stream()).join();

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("spring.datasource.url", MYSQL.getJdbcUrl());
            properties.put("spring.datasource.username", MYSQL_USERNAME);
            properties.put("spring.datasource.password", MYSQL_PASSWORD);
            properties.put("spring.datasource.driver-class-name", MYSQL.getDriverClassName());

            properties.put("spring.rabbitmq.host", RABBITMQ.getHost());
            properties.put("spring.rabbitmq.port", RABBITMQ.getAmqpPort());
            properties.put("spring.rabbitmq.username", RABBITMQ_USERNAME);
            properties.put("spring.rabbitmq.password", RABBITMQ_PASSWORD);
            properties.put("spring.rabbitmq.virtual-host", RABBITMQ_VHOST);

            properties.put("spring.data.redis.host", REDIS.getHost());
            properties.put("spring.data.redis.port", REDIS.getMappedPort(REDIS_PORT));
            properties.put("spring.data.redis.password", REDIS_PASSWORD);

            info("容器就绪：MySQL " + MYSQL.getJdbcUrl()
                    + " / RabbitMQ " + RABBITMQ.getHost() + ":" + RABBITMQ.getAmqpPort()
                    + " / Redis " + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));

            return properties;
        }
    }
}
