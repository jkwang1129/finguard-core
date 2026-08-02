package com.finguard.core.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.auth.model.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RbacAuthorizationIntegrationTest {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");
    private static final String TEST_PREFIX = "RBAC-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanBusinessFixtures() {
        jdbcTemplate.update(
                """
                DELETE FROM transactions
                WHERE external_transaction_no LIKE ?
                """,
                TEST_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM accounts WHERE account_no LIKE ?",
                TEST_PREFIX + "%"
        );
    }

    @Test
    void adminCanCompleteEveryAccountAndTransactionOperation()
            throws Exception {
        long accountId = createAccount(
                "RBAC-ADMIN-MAIN",
                "RBAC admin main"
        );

        performAs(
                get("/api/accounts"),
                RoleCode.ADMIN
        ).andExpect(status().isOk());
        performAs(
                get("/api/accounts/{accountId}", accountId),
                RoleCode.ADMIN
        ).andExpect(status().isOk());

        long transactionId = createTransaction(
                accountId,
                "RBAC-ADMIN-TX-001"
        );
        performAs(
                get("/api/transactions"),
                RoleCode.ADMIN
        ).andExpect(status().isOk());
        performAs(
                get(
                        "/api/transactions/{transactionId}",
                        transactionId
                ),
                RoleCode.ADMIN
        ).andExpect(status().isOk());
        performAs(
                put("/api/transactions/{transactionId}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTransactionJson()),
                RoleCode.ADMIN
        ).andExpect(status().isOk());
        performAs(
                delete(
                        "/api/transactions/{transactionId}",
                        transactionId
                ),
                RoleCode.ADMIN
        ).andExpect(status().isNoContent());

        performAs(
                patch("/api/accounts/{accountId}/name", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "RBAC renamed"
                                }
                                """),
                RoleCode.ADMIN
        ).andExpect(status().isOk());
        performAs(
                patch("/api/accounts/{accountId}/status", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """),
                RoleCode.ADMIN
        ).andExpect(status().isOk());

        long emptyAccountId = createAccount(
                "RBAC-ADMIN-EMPTY",
                "RBAC admin empty"
        );
        performAs(
                patch(
                        "/api/accounts/{accountId}/status",
                        emptyAccountId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """),
                RoleCode.ADMIN
        ).andExpect(status().isOk());
        performAs(
                delete("/api/accounts/{accountId}", emptyAccountId),
                RoleCode.ADMIN
        ).andExpect(status().isNoContent());
    }

    @Test
    void reviewerCanReadButEveryWriteReturnsUniformForbidden()
            throws Exception {
        long accountId = createAccount(
                "RBAC-REVIEWER-MAIN",
                "RBAC reviewer main"
        );
        long transactionId = createTransaction(
                accountId,
                "RBAC-REVIEWER-TX-001"
        );

        performAs(
                get("/api/accounts"),
                RoleCode.REVIEWER
        ).andExpect(status().isOk());
        performAs(
                get("/api/accounts/{accountId}", accountId),
                RoleCode.REVIEWER
        ).andExpect(status().isOk());
        performAs(
                get("/api/transactions"),
                RoleCode.REVIEWER
        ).andExpect(status().isOk());
        performAs(
                get(
                        "/api/transactions/{transactionId}",
                        transactionId
                ),
                RoleCode.REVIEWER
        ).andExpect(status().isOk());

        int accountCountBefore = fixtureCount("accounts");
        int transactionCountBefore = fixtureCount("transactions");

        assertReviewerForbidden(
                post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNo": "RBAC-REVIEWER-DENIED",
                                  "accountName": "denied",
                                  "accountType": "BANK"
                                }
                                """),
                "/api/accounts"
        );
        assertReviewerForbidden(
                patch("/api/accounts/{accountId}/name", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "denied"
                                }
                                """),
                "/api/accounts/" + accountId + "/name"
        );
        assertReviewerForbidden(
                patch("/api/accounts/{accountId}/status", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """),
                "/api/accounts/" + accountId + "/status"
        );
        assertReviewerForbidden(
                delete("/api/accounts/{accountId}", accountId),
                "/api/accounts/" + accountId
        );
        assertReviewerForbidden(
                post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTransactionJson(
                                accountId,
                                "RBAC-REVIEWER-DENIED-TX"
                        )),
                "/api/transactions"
        );
        assertReviewerForbidden(
                put(
                        "/api/transactions/{transactionId}",
                        transactionId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTransactionJson()),
                "/api/transactions/" + transactionId
        );
        assertReviewerForbidden(
                delete(
                        "/api/transactions/{transactionId}",
                        transactionId
                ),
                "/api/transactions/" + transactionId
        );

        assertThat(fixtureCount("accounts"))
                .isEqualTo(accountCountBefore);
        assertThat(fixtureCount("transactions"))
                .isEqualTo(transactionCountBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT account_name FROM accounts WHERE id = ?",
                String.class,
                accountId
        )).isEqualTo("RBAC reviewer main");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM transactions WHERE id = ?",
                Integer.class,
                transactionId
        )).isZero();
    }

    @Test
    void anonymousUserReceivesUnauthorizedForEveryBusinessRoute()
            throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/accounts"),
                get("/api/accounts/1"),
                post("/api/accounts"),
                patch("/api/accounts/1/name"),
                patch("/api/accounts/1/status"),
                delete("/api/accounts/1"),
                get("/api/transactions"),
                get("/api/transactions/1"),
                post("/api/transactions"),
                put("/api/transactions/1"),
                delete("/api/transactions/1"),
                get("/api/review-tasks"),
                get("/api/review-tasks/1"),
                patch("/api/review-tasks/1/decision")
        );

        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(
                            HttpHeaders.WWW_AUTHENTICATE,
                            "Bearer"
                    ))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code")
                            .value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void authenticatedUserWithoutKnownRoleReceivesForbidden()
            throws Exception {
        RequestPostProcessor noAuthorities = jwt().authorities(List.of());

        mockMvc.perform(get("/api/accounts").with(noAuthorities))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.WWW_AUTHENTICATE
                ))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/transactions").with(noAuthorities))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.WWW_AUTHENTICATE
                ))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/review-tasks").with(noAuthorities))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(patch("/api/review-tasks/1/decision")
                        .with(noAuthorities))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private long createAccount(
            String accountNo,
            String accountName) throws Exception {
        String responseBody = performAs(
                post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNo": "%s",
                                  "accountName": "%s",
                                  "accountType": "BANK"
                                }
                                """.formatted(accountNo, accountName)),
                RoleCode.ADMIN
        )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return responseId(responseBody);
    }

    private long createTransaction(
            long accountId,
            String externalTransactionNo) throws Exception {
        String responseBody = performAs(
                post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTransactionJson(
                                accountId,
                                externalTransactionNo
                        )),
                RoleCode.ADMIN
        )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return responseId(responseBody);
    }

    private String createTransactionJson(
            long accountId,
            String externalTransactionNo) {
        return """
                {
                  "accountId": %d,
                  "externalTransactionNo": "%s",
                  "direction": "INCOME",
                  "amount": 10.00,
                  "transactionTime": "%s",
                  "description": "RBAC authorization test"
                }
                """.formatted(
                accountId,
                externalTransactionNo,
                businessTime()
        );
    }

    private String updateTransactionJson() {
        return """
                {
                  "direction": "EXPENSE",
                  "amount": 20.00,
                  "transactionTime": "%s",
                  "description": "RBAC authorization update"
                }
                """.formatted(businessTime());
    }

    private String businessTime() {
        return LocalDateTime.now(BUSINESS_ZONE)
                .minusMinutes(1)
                .truncatedTo(ChronoUnit.SECONDS)
                .toString();
    }

    private long responseId(String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        return response.required("id").longValue();
    }

    private ResultActions performAs(
            MockHttpServletRequestBuilder request,
            RoleCode role) throws Exception {
        return mockMvc.perform(request.with(role(role)));
    }

    private RequestPostProcessor role(RoleCode role) {
        return jwt().authorities(
                new SimpleGrantedAuthority("ROLE_" + role.name())
        );
    }

    private void assertReviewerForbidden(
            MockHttpServletRequestBuilder request,
            String expectedPath) throws Exception {
        performAs(request, RoleCode.REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.WWW_AUTHENTICATE
                ))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message")
                        .value(RestAccessDeniedHandler.ACCESS_DENIED_MESSAGE))
                .andExpect(jsonPath("$.path").value(expectedPath))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    private int fixtureCount(String table) {
        String sql = switch (table) {
            case "accounts" -> """
                    SELECT COUNT(*)
                    FROM accounts
                    WHERE account_no LIKE 'RBAC-%'
                    """;
            case "transactions" -> """
                    SELECT COUNT(*)
                    FROM transactions
                    WHERE external_transaction_no LIKE 'RBAC-%'
                    """;
            default -> throw new IllegalArgumentException(
                    "Unsupported fixture table"
            );
        };
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }
}
