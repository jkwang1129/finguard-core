package com.finguard.core.auth.controller;

import com.finguard.core.auth.dto.LoginRequest;
import com.finguard.core.auth.security.TokenIssuer;
import com.finguard.core.auth.service.AuthService;
import com.finguard.core.auth.vo.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
