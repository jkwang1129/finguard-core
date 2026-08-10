package com.finguard.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.config.OpenApiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiHttpIntegrationTest {

    private static final List<String> CORE_PATHS = List.of(
            "/api/auth/login",
            "/api/accounts",
            "/api/accounts/{accountId}",
            "/api/accounts/{accountId}/name",
            "/api/accounts/{accountId}/status",
            "/api/transactions",
            "/api/transactions/{transactionId}",
            "/api/import-jobs",
            "/api/import-jobs/{importJobId}",
            "/api/import-jobs/{importJobId}/errors",
            "/api/reconciliation-jobs",
            "/api/reconciliation-jobs/{reconciliationJobId}",
            "/api/reconciliation-jobs/{reconciliationJobId}/results",
            "/api/review-tasks",
            "/api/review-tasks/{reviewTaskId}",
            "/api/review-tasks/{reviewTaskId}/decision",
            "/api/audit-logs",
            "/api/statistics/overview"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anonymousOpenApiDocumentContainsCorePathsAndBearerScheme()
            throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(responseBody);
        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.path("info").path("title").asText())
                .isEqualTo("FinGuard Core API");
        assertThat(document.path("info").path("version").asText())
                .isEqualTo("v1");

        JsonNode bearerScheme = document
                .path("components")
                .path("securitySchemes")
                .path(OpenApiConfiguration.BEARER_AUTH);
        assertThat(bearerScheme.path("type").asText())
                .isEqualTo("http");
        assertThat(bearerScheme.path("scheme").asText())
                .isEqualTo("bearer");
        assertThat(bearerScheme.path("bearerFormat").asText())
                .isEqualTo("JWT");

        JsonNode globalSecurity = document.path("security");
        assertThat(globalSecurity.isArray()).isTrue();
        assertThat(globalSecurity).hasSize(1);
        assertThat(globalSecurity.get(0)
                .has(OpenApiConfiguration.BEARER_AUTH)).isTrue();

        JsonNode paths = document.path("paths");
        for (String path : CORE_PATHS) {
            assertThat(paths.has(path))
                    .as("OpenAPI path %s", path)
                    .isTrue();
        }

        JsonNode loginSecurity = paths
                .path("/api/auth/login")
                .path("post")
                .path("security");
        assertThat(loginSecurity.isArray()).isTrue();
        assertThat(loginSecurity).isEmpty();

        assertNoFrameworkParameter(
                paths.path("/api/auth/login").path("post")
        );
        assertNoFrameworkParameter(
                paths.path("/api/import-jobs").path("post")
        );
        assertNoFrameworkParameter(
                paths.path("/api/reconciliation-jobs").path("post")
        );
        assertNoFrameworkParameter(
                paths.path("/api/review-tasks/{reviewTaskId}/decision")
                        .path("patch")
        );

        assertThat(responseBody)
                .doesNotContain("secret-base64")
                .doesNotContain("MYSQL_PASSWORD")
                .doesNotContain("RABBITMQ_PASSWORD")
                .doesNotContain("REDIS_PASSWORD")
                .doesNotContain("JWT_SECRET_BASE64");
    }

    @Test
    void anonymousUserCanLoadSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_HTML
                ));
    }

    @Test
    void openingDocumentationDoesNotRelaxBusinessAuthentication()
            throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedUserWithoutRoleRemainsForbidden()
            throws Exception {
        RequestPostProcessor noAuthorities = jwt().authorities(List.of());

        mockMvc.perform(get("/api/accounts").with(noAuthorities))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.WWW_AUTHENTICATE
                ))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private void assertNoFrameworkParameter(JsonNode operation) {
        JsonNode parameters = operation.path("parameters");
        if (!parameters.isArray()) {
            return;
        }
        for (JsonNode parameter : parameters) {
            String name = parameter.path("name").asText();
            assertThat(name)
                    .isNotEqualToIgnoringCase("servletRequest")
                    .isNotEqualToIgnoringCase("jwt");
        }
    }
}
