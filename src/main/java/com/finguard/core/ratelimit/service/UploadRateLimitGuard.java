package com.finguard.core.ratelimit.service;

import com.finguard.core.ratelimit.exception.RateLimitExceededException;
import com.finguard.core.ratelimit.model.RateLimitDecision;
import com.finguard.core.ratelimit.model.RateLimitScope;
import com.finguard.core.redis.config.RedisFeatureProperties;
import org.springframework.stereotype.Component;

@Component
public class UploadRateLimitGuard {

    private final FixedWindowRateLimiter rateLimiter;
    private final RateLimitKeyFactory keyFactory;
    private final RedisFeatureProperties properties;

    public UploadRateLimitGuard(
            FixedWindowRateLimiter rateLimiter,
            RateLimitKeyFactory keyFactory,
            RedisFeatureProperties properties) {
        this.rateLimiter = rateLimiter;
        this.keyFactory = keyFactory;
        this.properties = properties;
    }

    public void check(long userId) {
        String key = keyFactory.uploadKey(userId);
        RedisFeatureProperties.Limit policy =
                properties.getRateLimit().getUpload();
        RateLimitDecision decision = rateLimiter.acquire(
                key,
                policy.getLimit(),
                policy.getWindow(),
                RateLimitScope.UPLOAD
        );
        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    decision.retryAfterSeconds()
            );
        }
    }
}
