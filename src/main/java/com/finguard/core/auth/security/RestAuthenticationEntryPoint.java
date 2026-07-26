package com.finguard.core.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.common.exception.ApiErrorResponse;
import com.finguard.core.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class RestAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    private static final String AUTHENTICATION_REQUIRED_MESSAGE =
            "Authentication is required";
    private static final String INVALID_TOKEN_MESSAGE =
            "Invalid or expired access token";

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException, ServletException {
        boolean invalidToken =
                exception instanceof OAuth2AuthenticationException;
        ErrorCode code = invalidToken
                ? ErrorCode.INVALID_TOKEN
                : ErrorCode.AUTHENTICATION_REQUIRED;
        String message = invalidToken
                ? INVALID_TOKEN_MESSAGE
                : AUTHENTICATION_REQUIRED_MESSAGE;

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                code,
                message,
                request.getRequestURI(),
                List.of()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
