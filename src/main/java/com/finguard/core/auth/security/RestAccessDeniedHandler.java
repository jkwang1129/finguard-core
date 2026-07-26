package com.finguard.core.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.common.exception.ApiErrorResponse;
import com.finguard.core.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    static final String ACCESS_DENIED_MESSAGE =
            "Insufficient permissions";

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception)
            throws IOException, ServletException {
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.ACCESS_DENIED,
                ACCESS_DENIED_MESSAGE,
                request.getRequestURI(),
                List.of()
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
