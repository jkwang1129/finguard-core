package com.finguard.core.auth.controller;

import com.finguard.core.auth.dto.LoginRequest;
import com.finguard.core.auth.exception.InvalidCredentialsException;
import com.finguard.core.auth.service.AuthService;
import com.finguard.core.auth.vo.LoginResponse;
import com.finguard.core.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void loginReturnsBearerResponseFromRealServiceContract()
            throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse(
                        "signed-test-token",
                        7200
                ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Exact Password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("signed-test-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(7200));
    }

    @Test
    void loginReturnsUnifiedUnauthorizedResponse()
            throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Wrong Password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid username or password"))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/login"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void loginRejectsInvalidFieldsBeforeCallingService()
            throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": " ",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()")
                        .value(2));

        verifyNoInteractions(authService);
    }

    @Test
    void loginRejectsMalformedJsonBeforeCallingService()
            throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password":
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST"));

        verifyNoInteractions(authService);
    }
}
