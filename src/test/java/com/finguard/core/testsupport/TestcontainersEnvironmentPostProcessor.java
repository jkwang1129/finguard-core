package com.finguard.core.testsupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 把 {@link FinGuardTestContainers} 的连接信息注入 Spring {@code Environment}。
 *
 * <p><b>为什么用 {@link EnvironmentPostProcessor} 而不是
 * {@code context.initializer.classes}？</b>
 * 后者在 Spring Boot 3.x 中并不存在（只有 {@code context.listener.classes}），
 * 写进配置会被静默忽略——测试仍然连到本机 localhost，看起来「通过」但其实是假通过。
 * {@link EnvironmentPostProcessor} 才是官方支持、且在
 * {@code spring.factories} 中仍然生效的扩展点。
 *
 * <p><b>为什么不用 {@code @DynamicPropertySource}？</b>
 * 本仓库有 40+ 个 {@code @SpringBootTest} 类，逐个加基类既啰嗦又容易漏。
 * 这里通过 {@code META-INF/spring.factories} 注册一次即可覆盖全部集成测试，
 * 也不用把测试类耦合到某个基类上。
 *
 * <p><b>优先级：</b>实现 {@link Ordered} 并返回 {@link Ordered#LOWEST_PRECEDENCE}，
 * 确保在所有 {@code EnvironmentPostProcessor}（尤其是处理
 * {@code spring.config.import} 的 {@code ConfigDataEnvironmentPostProcessor}）之后执行，
 * 再用 {@code addFirst} 把容器地址放到最高优先级，从而覆盖 {@code .env}、
 * {@code application.yml} 与测试属性文件中的本机地址。
 */
public class TestcontainersEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "testcontainers";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                FinGuardTestContainers.springProperties()
        ));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
