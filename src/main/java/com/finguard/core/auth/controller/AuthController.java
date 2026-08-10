package com.finguard.core.auth.controller;

import com.finguard.core.auth.dto.LoginRequest;
import com.finguard.core.auth.service.AuthService;
import com.finguard.core.auth.vo.LoginResponse;
import com.finguard.core.ratelimit.model.LoginRateLimitPermit;
import com.finguard.core.ratelimit.service.LoginRateLimitGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
