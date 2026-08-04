package com.finguard.core.ratelimit.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RateLimitKeyFactory {

    private static final String LOGIN_PREFIX =
            "finguard:ratelimit:login:v1:";

    private static final String UPLOAD_PREFIX =
            "finguard:ratelimit:upload:v1:user:";

    public String loginKey(
            String remoteAddress,
            String normalizedUsername) {
        return LOGIN_PREFIX
                + sha256(requireText(remoteAddress, "remoteAddress"))
                + ':'
                + sha256(requireText(
                        normalizedUsername,
                        "normalizedUsername"
                ));
    }

    public String uploadKey(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return UPLOAD_PREFIX + userId;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
