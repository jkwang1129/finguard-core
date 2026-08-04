package com.finguard.core.ratelimit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitKeyFactoryTest {

    private final RateLimitKeyFactory keyFactory =
            new RateLimitKeyFactory();

    @Test
    void loginKeyIsDeterministicAndContainsNoPlaintextInput() {
        String key = keyFactory.loginKey(
                "192.0.2.10",
                "sensitive-user"
        );

        assertThat(key)
                .startsWith("finguard:ratelimit:login:v1:")
                .hasSize(28 + 64 + 1 + 64)
                .doesNotContain("192.0.2.10")
                .doesNotContain("sensitive-user");
        assertThat(keyFactory.loginKey(
                "192.0.2.10",
                "sensitive-user"
        )).isEqualTo(key);
        assertThat(keyFactory.loginKey(
                "192.0.2.11",
                "sensitive-user"
        )).isNotEqualTo(key);
    }

    @Test
    void uploadKeyUsesOnlyValidatedAuthenticatedUserId() {
        assertThat(keyFactory.uploadKey(42))
                .isEqualTo("finguard:ratelimit:upload:v1:user:42");
        assertThatThrownBy(() -> keyFactory.uploadKey(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
