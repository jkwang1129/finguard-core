package com.finguard.core.redis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisFeaturePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsValidCacheAndRateLimitConfiguration() {
        contextRunner.withPropertyValues(
                "finguard.redis.statistics.cache-ttl=90s",
                "finguard.redis.rate-limit.login.limit=7",
                "finguard.redis.rate-limit.login.window=6m",
                "finguard.redis.rate-limit.upload.limit=12",
                "finguard.redis.rate-limit.upload.window=2m"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            RedisFeatureProperties properties = context.getBean(
                    RedisFeatureProperties.class
            );
            assertThat(properties.getStatistics().getCacheTtl())
                    .isEqualTo(Duration.ofSeconds(90));
            assertThat(properties.getRateLimit().getLogin().getLimit())
                    .isEqualTo(7);
            assertThat(properties.getRateLimit().getLogin().getWindow())
                    .isEqualTo(Duration.ofMinutes(6));
            assertThat(properties.getRateLimit().getUpload().getLimit())
                    .isEqualTo(12);
            assertThat(properties.getRateLimit().getUpload().getWindow())
                    .isEqualTo(Duration.ofMinutes(2));
        });
    }

    @Test
    void rejectsInvalidTtlLimitAndWindow() {
        assertInvalid("finguard.redis.statistics.cache-ttl=0s");
        assertInvalid("finguard.redis.statistics.cache-ttl=500ms");
        assertInvalid("finguard.redis.rate-limit.login.limit=0");
        assertInvalid("finguard.redis.rate-limit.upload.limit=10001");
        assertInvalid("finguard.redis.rate-limit.login.window=0s");
        assertInvalid("finguard.redis.rate-limit.upload.window=2d");
    }

    private void assertInvalid(String property) {
        contextRunner.withPropertyValues(property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RedisFeatureProperties.class)
    static class TestConfiguration {
    }
}
