package com.finguard.core.ratelimit.exception;

public class RateLimitExceededException extends RuntimeException {

    public static final String MESSAGE =
            "Request rate limit exceeded";

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(MESSAGE);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException(
                    "retryAfterSeconds must be positive"
            );
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
