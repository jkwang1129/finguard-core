package com.finguard.core.ratelimit.service;

import com.finguard.core.ratelimit.model.RateLimitDecision;
import com.finguard.core.ratelimit.model.RateLimitScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class FixedWindowRateLimiter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FixedWindowRateLimiter.class);

    private static final String RATE_LIMIT_PREFIX =
            "finguard:ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script;

    public FixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource(
                "redis/fixed-window-rate-limit.lua"
        ));
        this.script.setResultType(List.class);
    }

    public RateLimitDecision acquire(
            String key,
            int limit,
            Duration window,
            RateLimitScope scope) {
        validate(key, limit, window, scope);
        long windowMillis = window.toMillis();
        try {
            List<?> result = redisTemplate.execute(
                    script,
                    List.of(key),
                    Long.toString(windowMillis)
            );
            return toDecision(result, limit);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Redis rate limit unavailable for {}; failing open",
                    scope,
                    exception
            );
            return RateLimitDecision.allowDegraded();
        }
    }

    public void reset(String key, RateLimitScope scope) {
        validateKeyAndScope(key, scope);
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Redis rate limit reset unavailable for {}; "
                            + "continuing successful request",
                    scope,
                    exception
            );
        }
    }

    private RateLimitDecision toDecision(
            List<?> result,
            int limit) {
        if (result == null || result.size() != 2) {
            throw new IllegalStateException(
                    "Redis rate limit script returned an invalid result"
            );
        }
        long count = toLong(result.get(0), "count");
        long ttlMillis = toLong(result.get(1), "ttl");
        if (count <= 0 || ttlMillis < 0) {
            throw new IllegalStateException(
                    "Redis rate limit script returned invalid values"
            );
        }
        if (count <= limit) {
            return RateLimitDecision.allowRequest();
        }
        long retryAfterSeconds = Math.max(
                1,
                Math.floorDiv(ttlMillis + 999, 1000)
        );
        return RateLimitDecision.rejectRequest(retryAfterSeconds);
    }

    private long toLong(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException(
                "Redis rate limit " + field + " is not numeric"
        );
    }

    private void validate(
            String key,
            int limit,
            Duration window,
            RateLimitScope scope) {
        validateKeyAndScope(key, scope);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (window == null
                || window.isZero()
                || window.isNegative()
                || window.toMillis() < 1) {
            throw new IllegalArgumentException(
                    "window must be at least one millisecond"
            );
        }
    }

    private void validateKeyAndScope(
            String key,
            RateLimitScope scope) {
        if (key == null || !key.startsWith(RATE_LIMIT_PREFIX)) {
            throw new IllegalArgumentException(
                    "rate limit key must use the FinGuard namespace"
            );
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
    }
}
