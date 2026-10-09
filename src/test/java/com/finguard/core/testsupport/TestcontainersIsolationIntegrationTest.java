package com.finguard.core.testsupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.ClassUtils;
import java.util.Map;
import java.util.Properties;
import static org.assertj.core.api.Assertions.assertThat;

class TestcontainersIsolationIntegrationTest {
    @Test
    void postProcessorIsRegistered() throws Exception {
        Properties factories = new Properties();
        var resource = getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories");
        if (resource != null) {
            try (resource) { factories.load(resource); }
        }
        assertThat(factories.getProperty(EnvironmentPostProcessor.class.getName()))
                .contains("com.finguard.core.testsupport.TestcontainersEnvironmentPostProcessor");
    }

    @Test
    void containerPropertiesOverrideLocalAddresses() throws Exception {
        String name = "com.finguard.core.testsupport.TestcontainersEnvironmentPostProcessor";
        assertThat(ClassUtils.isPresent(name, getClass().getClassLoader())).isTrue();
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("conflicting-local", Map.of(
                "spring.datasource.url", "jdbc:mysql://localhost:1/wrong",
                "spring.rabbitmq.port", 1,
                "spring.data.redis.port", 1)));
        var processor = (EnvironmentPostProcessor) Class.forName(name).getConstructor().newInstance();
        processor.postProcessEnvironment(environment, new SpringApplication());
        var source = environment.getPropertySources().get("testcontainers");
        assertThat(source).isNotNull();
        assertThat(environment.getProperty("spring.datasource.url")).isNotEqualTo("jdbc:mysql://localhost:1/wrong");
        assertThat(environment.getProperty("spring.rabbitmq.port")).isEqualTo(source.getProperty("spring.rabbitmq.port").toString()).isNotEqualTo("1");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo(source.getProperty("spring.data.redis.port").toString()).isNotEqualTo("1");
        processor.postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getPropertySources().get("testcontainers")).isSameAs(source);
    }
}
