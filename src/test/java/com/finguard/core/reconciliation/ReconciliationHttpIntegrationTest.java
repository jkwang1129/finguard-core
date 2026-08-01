package com.finguard.core.reconciliation;

import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ReconciliationTestFixture fixture;
    private Long ownerId;
    private Long importJobId;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
        ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        LocalDateTime time = LocalDateTime.of(2026, 7, 31, 9, 0);
        fixture.insertTransaction(
                accountId,
                importJobId,
                "HTTP-EXACT",
                TransactionDirection.INCOME,
                "10.00",
                time,
                TransactionSource.CSV_IMPORT
        );
        fixture.insertTransaction(
                accountId,
                null,
                "HTTP-EXACT",
                TransactionDirection.INCOME,
                "10.00",
                time,
                TransactionSource.MANUAL
        );
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void adminShouldCreateAndReviewerShouldQuery() throws Exception {
        String location = mockMvc.perform(
                        post("/api/reconciliation-jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(importJobId))
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/reconciliation-jobs/[0-9]+"
                        )
                ))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.matchedCount").value(0))
                .andExpect(jsonPath("$.duplicateRequest").value(false))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).isNotNull();
        long jobId = Long.parseLong(
                location.substring(location.lastIndexOf('/') + 1)
        );
        mockMvc.perform(
                        get(
                                "/api/reconciliation-jobs/{id}",
                                jobId
                        ).with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId));
        mockMvc.perform(
                        get(
                                "/api/reconciliation-jobs/{id}/results",
                                jobId
                        )
                                .param("resultType", "MATCHED")
                                .with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.records").isEmpty());

        mockMvc.perform(
                        post("/api/reconciliation-jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(importJobId))
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId))
                .andExpect(jsonPath("$.duplicateRequest").value(true));
    }

    @Test
    void securityShouldRejectReviewerAndAnonymousWrites()
            throws Exception {
        int before = jobCount();

        mockMvc.perform(
                        post("/api/reconciliation-jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(importJobId))
                                .with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(
                        post("/api/reconciliation-jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(importJobId))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/reconciliation-jobs/1"))
                .andExpect(status().isUnauthorized());

        assertThat(jobCount()).isEqualTo(before);
    }

    @Test
    void errorsShouldUseUniformStatusesAndCodes() throws Exception {
        Long failedImportJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.FAILED,
                0
        );

        mockMvc.perform(
                        post("/api/reconciliation-jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(failedImportJobId))
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_RECONCILIATION_OPERATION"));
        mockMvc.perform(
                        post("/api/reconciliation-jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(Long.MAX_VALUE))
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("IMPORT_JOB_NOT_FOUND"));
        mockMvc.perform(
                        post("/api/reconciliation-jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"importJobId\":0}")
                                .with(as(RoleCode.ADMIN))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
        mockMvc.perform(
                        get(
                                "/api/reconciliation-jobs/{id}",
                                Long.MAX_VALUE
                        ).with(as(RoleCode.REVIEWER))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("RECONCILIATION_JOB_NOT_FOUND"));
        assertThat(jobCount()).isZero();
    }

    private RequestPostProcessor as(RoleCode role) {
        return jwt()
                .jwt(builder -> builder
                        .subject(ownerId.toString())
                        .claim("username", "week3-day6-http")
                        .claim("roles", List.of(role.name()))
                )
                .authorities(new SimpleGrantedAuthority(
                        "ROLE_" + role.name()
                ));
    }

    private String body(Long value) {
        return "{\"importJobId\":" + value + "}";
    }

    private int jobCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reconciliation_jobs
                WHERE created_by = ?
                """,
                Integer.class,
                ownerId
        );
    }
}
