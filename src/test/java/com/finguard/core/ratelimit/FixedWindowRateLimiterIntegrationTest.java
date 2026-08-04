package com.finguard.core.ratelimit;

import com.finguard.core.ratelimit.model.RateLimitDecision;
import com.finguard.core.ratelimit.model.RateLimitScope;
import com.finguard.core.ratelimit.service.FixedWindowRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "finguard.messaging.import-consumer.enabled=false",
        "finguard.messaging.reconciliation-consumer.enabled=false",
        "finguard.messaging.outbox.enabled=false"
})
class FixedWindowRateLimiterIntegrationTest {

    private final List<String> keys = new ArrayList<>();

    @Autowired
    private FixedWindowRateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanKeys() {
        redisTemplate.delete(keys);
    }

    @Test
    void allowsThresholdRejectsNextAndDoesNotExtendWindow() {
        String key = newKey("sequential");
        Duration window = Duration.ofSeconds(30);

        assertThat(acquire(key, 2, window).allowed()).isTrue();
        Long firstTtl = redisTemplate.getExpire(
                key,
                TimeUnit.MILLISECONDS
        );
        assertThat(acquire(key, 2, window).allowed()).isTrue();
        RateLimitDecision rejected = acquire(key, 2, window);
        Long finalTtl = redisTemplate.getExpire(
                key,
                TimeUnit.MILLISECONDS
        );

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 30L);
        assertThat(firstTtl).isPositive();
        assertThat(finalTtl).isPositive().isLessThanOrEqualTo(firstTtl);
    }

    @Test
    void repairsCounterThatHasNoExpiry() {
        String key = newKey("repair");
        redisTemplate.opsForValue().set(key, "4");
        assertThat(redisTemplate.getExpire(key)).isEqualTo(-1);

        RateLimitDecision decision = acquire(
                key,
                5,
                Duration.ofSeconds(20)
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, 20L);
    }

    @Test
    void concurrentRequestsNeverAllowMoreThanThreshold()
            throws Exception {
        String key = newKey("concurrent");
        int attempts = 20;
        int limit = 5;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<RateLimitDecision>> tasks = new ArrayList<>();
            for (int index = 0; index < attempts; index++) {
                tasks.add(() -> acquire(
                        key,
                        limit,
                        Duration.ofSeconds(30)
                ));
            }
            long allowed = executor.invokeAll(tasks)
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .filter(RateLimitDecision::allowed)
                    .count();

            assertThat(allowed).isEqualTo(limit);
            assertThat(redisTemplate.opsForValue().get(key))
                    .isEqualTo(Integer.toString(attempts));
        } finally {
            executor.shutdownNow();
        }
    }

    private RateLimitDecision acquire(
            String key,
            int limit,
            Duration window) {
        return rateLimiter.acquire(
                key,
                limit,
                window,
                RateLimitScope.LOGIN
        );
    }

    private String newKey(String marker) {
        String key = "finguard:ratelimit:test:v1:"
                + marker
                + ':'
                + UUID.randomUUID();
        keys.add(key);
        return key;
    }
}
