package com.finguard.core.ratelimit.service;

import com.finguard.core.ratelimit.model.RateLimitDecision;
import com.finguard.core.ratelimit.model.RateLimitScope;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedWindowRateLimiterTest {

    private final StringRedisTemplate redisTemplate =
            mock(StringRedisTemplate.class);

    private final FixedWindowRateLimiter rateLimiter =
            new FixedWindowRateLimiter(redisTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void redisDataAccessFailureFailsOpen() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(String.class)
        )).thenThrow(new DataAccessResourceFailureException("offline"));

        RateLimitDecision decision = rateLimiter.acquire(
                "finguard:ratelimit:test:v1:offline",
                1,
                Duration.ofSeconds(30),
                RateLimitScope.LOGIN
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.degraded()).isTrue();
    }

    @Test
    void resetFailureDoesNotBreakSuccessfulRequest() {
        when(redisTemplate.delete(anyString())).thenThrow(
                new DataAccessResourceFailureException("offline")
        );

        assertThatCode(() -> rateLimiter.reset(
                "finguard:ratelimit:login:v1:safe",
                RateLimitScope.LOGIN
        )).doesNotThrowAnyException();
    }
}
