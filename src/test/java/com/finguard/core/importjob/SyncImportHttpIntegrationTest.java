package com.finguard.core.importjob;

import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.importjob.support.ImportJobTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SyncImportHttpIntegrationTest {

    private static final String ACCOUNT_PREFIX = "W3D5_HTTP_";
    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ImportJobTestFixture fixture;
    private Long ownerId;
    private String accountNo;

    @BeforeEach
    void setUp() {
        fixture = new ImportJobTestFixture(jdbcTemplate);
        clean();
        ownerId = fixture.insertUser(UUID.randomUUID().toString());
        accountNo = ACCOUNT_PREFIX
                + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    account_no,
                    account_name,
                    account_type,
                    currency,
                    status,
                    deleted
                )
                VALUES (?, ?, 'BANK', 'CNY', 'ACTIVE', 0)
                """,
                accountNo,
                "Week 3 Day 5 HTTP"
        );
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void adminShouldUploadAndReviewerShouldQueryTaskAndErrors()
            throws Exception {
        String transactionNo = "HTTP-" + shortToken();
        MockMultipartFile file = csvFile(
                "C:\\fakepath\\transactions.csv",
                accountNo + "," + transactionNo
                        + ",INCOME,10.00,"
                        + "2026-07-31 09:00:00,http test"
        );

        String location = mockMvc.perform(
                        multipart("/api/import-jobs")
                                .file(file)
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/import-jobs/[0-9]+"
                        )
                ))
                .andExpect(jsonPath("$.originalFileName")
                        .value("transactions.csv"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalRows").value(0))
                .andExpect(jsonPath("$.successRows").value(0))
                .andExpect(jsonPath("$.duplicateFile").value(false))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).isNotNull();
        long importJobId = Long.parseLong(
                location.substring(location.lastIndexOf('/') + 1)
        );

        mockMvc.perform(
                        get("/api/import-jobs/{id}", importJobId)
                                .with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(importJobId))
                .andExpect(jsonPath("$.duplicateFile").value(false));

        mockMvc.perform(
                        get("/api/import-jobs/{id}/errors", importJobId)
                                .param("page", "1")
                                .param("size", "20")
                                .with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.records").isEmpty());

        mockMvc.perform(
                        multipart("/api/import-jobs")
                                .file(file)
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(importJobId))
                .andExpect(jsonPath("$.duplicateFile").value(true));
    }

    @Test
    void securityShouldRejectReviewerUploadAndAnonymousAccess()
            throws Exception {
        MockMultipartFile file = csvFile(
                "permissions.csv",
                accountNo + ",HTTP-" + shortToken()
                        + ",INCOME,10.00,"
                        + "2026-07-31 09:00:00,permissions"
        );
        int jobsBefore = jobCount();

        mockMvc.perform(
                        multipart("/api/import-jobs")
                                .file(file)
                                .with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(multipart("/api/import-jobs").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/import-jobs/1"))
                .andExpect(status().isUnauthorized());

        assertThat(jobCount()).isEqualTo(jobsBefore);
    }

    @Test
    void requestAndQueryErrorsShouldUseUnifiedSafeResponses()
            throws Exception {
        int jobsBefore = jobCount();

        mockMvc.perform(
                        multipart("/api/import-jobs")
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_IMPORT_FILE"))
                .andExpect(jsonPath("$.message")
                        .value("CSV file is required"));

        mockMvc.perform(
                        multipart("/api/import-jobs")
                                .file(new MockMultipartFile(
                                        "file",
                                        "too-large.csv",
                                        "text/csv",
                                        new byte[
                                                5 * 1024 * 1024 + 1
                                                ]
                                ))
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_IMPORT_FILE"));

        mockMvc.perform(
                        get("/api/import-jobs/{id}", Long.MAX_VALUE)
                                .with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("IMPORT_JOB_NOT_FOUND"));

        mockMvc.perform(
                        get(
                                "/api/import-jobs/{id}/errors",
                                Long.MAX_VALUE
                        )
                                .param("size", "101")
                                .with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));

        assertThat(jobCount()).isEqualTo(jobsBefore);
    }

    private RequestPostProcessor as(RoleCode role) {
        return jwt()
                .jwt(builder -> builder
                        .subject(ownerId.toString())
                        .claim("username", "week3-day5-http")
                        .claim("roles", List.of(role.name()))
                )
                .authorities(new SimpleGrantedAuthority(
                        "ROLE_" + role.name()
                ));
    }

    private MockMultipartFile csvFile(
            String originalFileName,
            String... rows) {
        byte[] bytes = (HEADER + "\n"
                + String.join("\n", rows)
                + "\n").getBytes(StandardCharsets.UTF_8);
        return new MockMultipartFile(
                "file",
                originalFileName,
                "text/csv",
                bytes
        );
    }

    private String shortToken() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private int jobCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM import_jobs
                WHERE created_by = ?
                """,
                Integer.class,
                ownerId
        );
    }

    private void clean() {
        fixture.clean();
        jdbcTemplate.update(
                """
                DELETE t
                FROM transactions t
                INNER JOIN accounts a ON a.id = t.account_id
                WHERE a.account_no LIKE ?
                """,
                ACCOUNT_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM accounts WHERE account_no LIKE ?",
                ACCOUNT_PREFIX + "%"
        );
    }
}
