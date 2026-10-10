package com.finguard.core.auth.controller;

import com.finguard.core.auth.dto.LoginRequest;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.service.AuthService;
import com.finguard.core.auth.vo.CurrentUserResponse;
import com.finguard.core.auth.vo.LoginResponse;
import com.finguard.core.config.OpenApiConfiguration;
import com.finguard.core.ratelimit.model.LoginRateLimitPermit;
import com.finguard.core.ratelimit.service.LoginRateLimitGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "JWT authentication")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimitGuard loginRateLimitGuard;

    public AuthController(
            AuthService authService,
            LoginRateLimitGuard loginRateLimitGuard) {
        this.authService = authService;
        this.loginRateLimitGuard = loginRateLimitGuard;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and issue a JWT")
    @SecurityRequirements
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            @Parameter(hidden = true)
            HttpServletRequest servletRequest) {
        LoginRateLimitPermit permit = loginRateLimitGuard.check(
                servletRequest.getRemoteAddr(),
                request.getUsername()
        );
        LoginResponse response = authService.login(request);
        loginRateLimitGuard.onAuthenticationSuccess(permit);
        return response;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user identity")
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    public CurrentUserResponse me(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("username");
        if (!StringUtils.hasText(username)) {
            throw invalidIdentityClaims();
        }
        return new CurrentUserResponse(username, roles(jwt));
    }

    private List<RoleCode> roles(Jwt jwt) {
        Object claim = jwt.getClaim("roles");
        if (!(claim instanceof Collection<?> values)) {
            throw invalidIdentityClaims();
        }
        return values.stream()
                .map(this::role)
                .distinct()
                .sorted(Comparator.comparing(RoleCode::name))
                .toList();
    }

    private RoleCode role(Object value) {
        if (!(value instanceof String role)) {
            throw invalidIdentityClaims();
        }
        try {
            return RoleCode.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw invalidIdentityClaims();
        }
    }

    private IllegalStateException invalidIdentityClaims() {
        return new IllegalStateException(
                "Authenticated JWT identity claims are invalid"
        );
    }
}
