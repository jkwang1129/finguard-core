package com.finguard.core.auth.security;

import com.finguard.core.auth.model.AuthenticatedUser;

@FunctionalInterface
public interface TokenIssuer {

    String issueToken(AuthenticatedUser authenticatedUser);
}
