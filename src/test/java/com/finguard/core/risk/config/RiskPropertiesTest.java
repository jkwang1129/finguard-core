package com.finguard.core.risk.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RiskPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldBindValidConfiguration() {
        contextRunner.withPropertyValues(
                "finguard.risk.large-amount.threshold=12000.50",
                "finguard.risk.possible-duplicate.window=7m",
                "finguard.risk.frequent-transaction.window=12m",
                "finguard.risk.frequent-transaction.threshold-count=6"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            RiskProperties properties = context.getBean(
                    RiskProperties.class
            );
            assertThat(properties.getLargeAmount().getThreshold())
                    .isEqualByComparingTo("12000.50");
            assertThat(properties.getPossibleDuplicate()
                    .windowSeconds()).isEqualTo(420);
            assertThat(properties.getFrequentTransaction()
                    .windowSeconds()).isEqualTo(720);
            assertThat(properties.getFrequentTransaction()
                    .getThresholdCount()).isEqualTo(6);
        });
    }

    @Test
    void shouldRejectInvalidAmountScaleWindowAndCount() {
        assertInvalid("finguard.risk.large-amount.threshold=1.001");
        assertInvalid("finguard.risk.possible-duplicate.window=0s");
        assertInvalid("finguard.risk.frequent-transaction.window=500ms");
        assertInvalid("finguard.risk.frequent-transaction.threshold-count=0");
    }

    private void assertInvalid(String property) {
        contextRunner.withPropertyValues(property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RiskProperties.class)
    static class TestConfiguration {
    }
}
