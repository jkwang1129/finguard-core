package com.finguard.core.auth.validation;

import java.util.Locale;

public final class AuthInputNormalizer {

    private AuthInputNormalizer() {
    }

    public static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
