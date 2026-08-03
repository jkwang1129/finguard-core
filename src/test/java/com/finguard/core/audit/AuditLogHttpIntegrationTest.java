package com.finguard.core.audit;

import com.finguard.core.audit.service.AuditLogService;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ReconciliationTestFixture fixture;
    private Long ownerId;
    private Long importJobId;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
        ownerId = fixture.insertUser();
        importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.FAILED,
                0
        );
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> {
                    auditLogService.recordCsvUploadAccepted(
                            importJobId,
                            ownerId
                    );
                    auditLogService.recordImportFailed(
                            importJobId,
                            ownerId
                    );
                }
        );
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void adminShouldQueryStableSafePageAndFilters() throws Exception {
        RequestPostProcessor admin = role(RoleCode.ADMIN, ownerId);

        mockMvc.perform(get("/api/audit-logs").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.records[0].actionCode")
                        .value("IMPORT_FAILED"))
                .andExpect(jsonPath("$.records[0].actorType")
                        .value("SYSTEM"))
                .andExpect(jsonPath("$.records[0].actorUserId")
                        .doesNotExist())
                .andExpect(jsonPath("$.records[0].initiatedBy")
                        .value(ownerId))
                .andExpect(jsonPath("$.records[0].importJobId")
                        .value(importJobId))
                .andExpect(jsonPath("$.records[0].summary")
                        .value("Import failed"))
                .andExpect(jsonPath("$.records[0].password")
                        .doesNotExist())
                .andExpect(jsonPath("$.records[0].token")
                        .doesNotExist())
                .andExpect(jsonPath("$.records[0].decisionNote")
                        .doesNotExist());

        mockMvc.perform(get("/api/audit-logs")
                        .param("actionCode", "CSV_UPLOAD_ACCEPTED")
                        .param("initiatedBy", ownerId.toString())
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].actionCode")
                        .value("CSV_UPLOAD_ACCEPTED"));
    }

    @Test
    void invalidParametersAndUnauthorizedRolesShouldHaveNoSideEffects()
            throws Exception {
        int before = auditCount();

        mockMvc.perform(get("/api/audit-logs")
                        .param("page", "0")
                        .with(role(RoleCode.ADMIN, ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/audit-logs")
                        .param("size", "101")
                        .with(role(RoleCode.ADMIN, ownerId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/audit-logs")
                        .param("initiatedBy", "0")
                        .with(role(RoleCode.ADMIN, ownerId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/audit-logs")
                        .param("actionCode", "UNKNOWN")
                        .with(role(RoleCode.ADMIN, ownerId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/audit-logs")
                        .with(role(RoleCode.REVIEWER, ownerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/audit-logs")
                        .with(jwt()
                                .jwt(builder -> builder.subject(
                                        ownerId.toString()
                                ))
                                .authorities(List.of())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized());

        assertThat(auditCount()).isEqualTo(before);
    }

    private RequestPostProcessor role(RoleCode role, Long subject) {
        return jwt()
                .jwt(builder -> builder.subject(subject.toString()))
                .authorities(new SimpleGrantedAuthority(
                        "ROLE_" + role.name()
                ));
    }

    private int auditCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE initiated_by = ?",
                Integer.class,
                ownerId
        );
        return count == null ? 0 : count;
    }
}
