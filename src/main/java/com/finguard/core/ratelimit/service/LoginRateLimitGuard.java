package com.finguard.core.ratelimit.service;

import com.finguard.core.auth.validation.AuthInputNormalizer;
import com.finguard.core.ratelimit.exception.RateLimitExceededException;
import com.finguard.core.ratelimit.model.LoginRateLimitPermit;
import com.finguard.core.ratelimit.model.RateLimitDecision;
import com.finguard.core.ratelimit.model.RateLimitScope;
import com.finguard.core.redis.config.RedisFeatureProperties;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimitGuard {

    private final FixedWindowRateLimiter rateLimiter;
    private final RateLimitKeyFactory keyFactory;
    private final RedisFeatureProperties properties;

    public LoginRateLimitGuard(
            FixedWindowRateLimiter rateLimiter,
            RateLimitKeyFactory keyFactory,
            RedisFeatureProperties properties) {
        this.rateLimiter = rateLimiter;
        this.keyFactory = keyFactory;
        this.properties = properties;
    }

    public LoginRateLimitPermit check(
            String remoteAddress,
            String username) {
        String normalizedUsername =
                AuthInputNormalizer.normalizeUsername(username);
        String key = keyFactory.loginKey(
                remoteAddress,
                normalizedUsername
        );
        RedisFeatureProperties.Limit policy =
                properties.getRateLimit().getLogin();
        RateLimitDecision decision = rateLimiter.acquire(
                key,
                policy.getLimit(),
                policy.getWindow(),
                RateLimitScope.LOGIN
        );
        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    decision.retryAfterSeconds()
            );
        }
        return new LoginRateLimitPermit(key);
    }

    public void onAuthenticationSuccess(LoginRateLimitPermit permit) {
        if (permit == null) {
            throw new IllegalArgumentException(
                    "login rate limit permit must not be null"
            );
        }
        rateLimiter.reset(permit.key(), RateLimitScope.LOGIN);
    }
}
