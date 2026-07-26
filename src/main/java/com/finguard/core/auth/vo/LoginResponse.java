package com.finguard.core.auth.vo;

public class LoginResponse {

    private static final String TOKEN_TYPE = "Bearer";

    private final String accessToken;
    private final String tokenType;
    private final long expiresInSeconds;

    public LoginResponse(String accessToken, long expiresInSeconds) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    "accessToken must not be blank"
            );
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException(
                    "expiresInSeconds must be positive"
            );
        }

        this.accessToken = accessToken;
        this.tokenType = TOKEN_TYPE;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    @Override
    public String toString() {
        return "LoginResponse{"
                + "accessToken='<redacted>'"
                + ", tokenType='" + tokenType + '\''
                + ", expiresInSeconds=" + expiresInSeconds
                + '}';
    }
}
