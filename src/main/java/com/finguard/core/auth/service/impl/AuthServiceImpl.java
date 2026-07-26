package com.finguard.core.auth.service.impl;

import com.finguard.core.auth.config.JwtProperties;
import com.finguard.core.auth.dto.LoginRequest;
import com.finguard.core.auth.exception.InvalidCredentialsException;
import com.finguard.core.auth.model.AuthenticatedUser;
import com.finguard.core.auth.security.AuthPrincipal;
import com.finguard.core.auth.security.TokenIssuer;
import com.finguard.core.auth.service.AuthService;
import com.finguard.core.auth.validation.AuthInputNormalizer;
import com.finguard.core.auth.vo.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthenticationManager authenticationManager;
    private final TokenIssuer tokenIssuer;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            TokenIssuer tokenIssuer,
            JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.tokenIssuer = tokenIssuer;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String normalizedUsername =
                AuthInputNormalizer.normalizeUsername(
                        request.getUsername()
                );

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken
                                    .unauthenticated(
                                            normalizedUsername,
                                            request.getPassword()
                                    )
                    );
            AuthPrincipal principal = requirePrincipal(authentication);
            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                    principal.getUserId(),
                    principal.getUsername(),
                    principal.getRoles()
            );
            String accessToken =
                    tokenIssuer.issueToken(authenticatedUser);
            return new LoginResponse(
                    accessToken,
                    jwtProperties.getExpiresInSeconds()
            );
        } catch (BadCredentialsException | DisabledException exception) {
            LOGGER.warn(
                    "Authentication rejected for username={}",
                    normalizedUsername
            );
            throw new InvalidCredentialsException();
        }
    }

    private AuthPrincipal requirePrincipal(
            Authentication authentication) {
        if (authentication.getPrincipal()
                instanceof AuthPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException(
                "Authentication principal has an unexpected type"
        );
    }
}
