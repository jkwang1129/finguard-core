package com.finguard.core.ratelimit.model;

public record RateLimitDecision(
        boolean allowed,
        long retryAfterSeconds,
        boolean degraded) {

    public RateLimitDecision {
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException(
                    "retryAfterSeconds must not be negative"
            );
        }
        if (allowed && retryAfterSeconds != 0) {
            throw new IllegalArgumentException(
                    "allowed decisions must not have retry delay"
            );
        }
        if (!allowed && retryAfterSeconds < 1) {
            throw new IllegalArgumentException(
                    "rejected decisions require a retry delay"
            );
        }
        if (degraded && !allowed) {
            throw new IllegalArgumentException(
                    "degraded decisions must fail open"
            );
        }
    }

    public static RateLimitDecision allowRequest() {
        return new RateLimitDecision(true, 0, false);
    }

    public static RateLimitDecision rejectRequest(long retryAfterSeconds) {
        return new RateLimitDecision(
                false,
                retryAfterSeconds,
                false
        );
    }

    public static RateLimitDecision allowDegraded() {
        return new RateLimitDecision(true, 0, true);
    }
}
