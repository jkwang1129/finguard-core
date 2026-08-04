package com.finguard.core.ratelimit.model;

public record LoginRateLimitPermit(String key) {

    private static final String LOGIN_PREFIX =
            "finguard:ratelimit:login:v1:";

    public LoginRateLimitPermit {
        if (key == null || !key.startsWith(LOGIN_PREFIX)) {
            throw new IllegalArgumentException(
                    "login permit key is invalid"
            );
        }
    }
}
