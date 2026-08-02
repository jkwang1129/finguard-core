package com.finguard.core.review;

import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.entity.ReconciliationResult;
import com.finguard.core.reconciliation.mapper.ReconciliationResultMapper;
import com.finguard.core.reconciliation.model.ReconciliationMatchMethod;
import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.review.entity.ReviewTask;
import com.finguard.core.review.mapper.ReviewTaskMapper;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewTaskHttpIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 2, 14, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReconciliationResultMapper reconciliationResultMapper;
    @Autowired
    private ReviewTaskMapper reviewTaskMapper;

    private ReconciliationTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void adminAndReviewerCanQueryButOnlyReviewerCanDecide()
            throws Exception {
        Scenario scenario = createExceptionTask();

        mockMvc.perform(get("/api/review-tasks")
                        .with(role(RoleCode.ADMIN, scenario.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].id")
                        .value(scenario.taskId()))
                .andExpect(jsonPath("$.records[0].sourceType")
                        .value("RECONCILIATION_EXCEPTION"))
                .andExpect(jsonPath("$.records[0].resultType")
                        .value("UNMATCHED"));
        mockMvc.perform(get(
                        "/api/review-tasks/{reviewTaskId}",
                        scenario.taskId()
                ).with(role(RoleCode.REVIEWER, scenario.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .with(role(RoleCode.ADMIN, scenario.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson("CONFIRMED", 0, "denied")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(taskStatus(scenario.taskId()))
                .isEqualTo(ReviewTaskStatus.PENDING.name());

        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .with(role(RoleCode.REVIEWER, scenario.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson(
                                "CONFIRMED",
                                0,
                                "  verified  "
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.reviewedBy")
                        .value(scenario.ownerId()))
                .andExpect(jsonPath("$.decisionNote")
                        .value("verified"));
    }

    @Test
    void anonymousAndUnknownRoleAreRejectedBeforeBusinessLogic()
            throws Exception {
        Scenario scenario = createExceptionTask();

        mockMvc.perform(get("/api/review-tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson("IGNORED", 0, null)))
                .andExpect(status().isUnauthorized());

        RequestPostProcessor noRole = jwt()
                .jwt(builder -> builder.subject(
                        scenario.ownerId().toString()
                ))
                .authorities(List.of());
        mockMvc.perform(get("/api/review-tasks").with(noRole))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .with(noRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson("IGNORED", 0, null)))
                .andExpect(status().isForbidden());
        assertThat(taskStatus(scenario.taskId()))
                .isEqualTo(ReviewTaskStatus.PENDING.name());
    }

    @Test
    void validationNotFoundTerminalAndVersionErrorsAreStable()
            throws Exception {
        Scenario scenario = createExceptionTask();
        RequestPostProcessor reviewer = role(
                RoleCode.REVIEWER,
                scenario.ownerId()
        );

        mockMvc.perform(get("/api/review-tasks?page=0")
                        .with(reviewer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
        mockMvc.perform(get(
                        "/api/review-tasks?resultType=UNMATCHED"
                                + "&ruleCode=LARGE_AMOUNT"
                ).with(reviewer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/review-tasks/{id}", Long.MAX_VALUE)
                        .with(reviewer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_TASK_NOT_FOUND"));

        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .with(reviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson("PENDING", 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .with(reviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson(
                                "CONFIRMED",
                                0,
                                "n".repeat(256)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(taskStatus(scenario.taskId()))
                .isEqualTo(ReviewTaskStatus.PENDING.name());

        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .with(reviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson("CONFIRMED", 1, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_VERSION_CONFLICT"));
        assertThat(taskStatus(scenario.taskId()))
                .isEqualTo(ReviewTaskStatus.PENDING.name());

        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .with(reviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson("IGNORED", 0, null)))
                .andExpect(status().isOk());
        mockMvc.perform(patch(
                        "/api/review-tasks/{reviewTaskId}/decision",
                        scenario.taskId()
                )
                        .with(reviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson("CONFIRMED", 1, "retry")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REVIEW_OPERATION"));
        assertThat(taskStatus(scenario.taskId()))
                .isEqualTo(ReviewTaskStatus.IGNORED.name());
    }

    private Scenario createExceptionTask() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        Long transactionId = fixture.insertTransaction(
                accountId,
                importJobId,
                "REVIEW-HTTP-" + importJobId,
                TransactionDirection.EXPENSE,
                "25.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        jdbcTemplate.update(
                """
                INSERT INTO reconciliation_jobs (
                    import_job_id,
                    status,
                    total_count,
                    matched_count,
                    unmatched_count,
                    duplicate_count,
                    suspicious_count,
                    created_by,
                    started_at,
                    finished_at
                ) VALUES (?, 'COMPLETED', 1, 0, 1, 0, 0, ?, ?, ?)
                """,
                importJobId,
                ownerId,
                BASE_TIME,
                BASE_TIME.plusSeconds(1)
        );
        Long jobId = jdbcTemplate.queryForObject(
                "SELECT id FROM reconciliation_jobs WHERE import_job_id = ?",
                Long.class,
                importJobId
        );
        ReconciliationResult result = new ReconciliationResult();
        result.setReconciliationJobId(jobId);
        result.setCsvTransactionId(transactionId);
        result.setResultType(ReconciliationResultType.UNMATCHED);
        result.setMatchMethod(ReconciliationMatchMethod.NONE);
        result.setReasonCode(ReconciliationReasonCode.NO_CANDIDATE);
        assertThat(reconciliationResultMapper.insertBatch(List.of(result)))
                .isEqualTo(1);
        result = reconciliationResultMapper.selectByJobId(jobId).get(0);

        ReviewTask task = new ReviewTask();
        task.setSourceType(
                ReviewTaskSourceType.RECONCILIATION_EXCEPTION
        );
        task.setReconciliationResultId(result.getId());
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setVersion(0);
        assertThat(reviewTaskMapper.insertBatch(List.of(task)))
                .isEqualTo(1);
        Long taskId = reviewTaskMapper.selectByReconciliationResultIds(
                List.of(result.getId())
        ).get(0).getId();
        return new Scenario(ownerId, taskId);
    }

    private RequestPostProcessor role(RoleCode role, Long subject) {
        return jwt()
                .jwt(builder -> builder.subject(subject.toString()))
                .authorities(new SimpleGrantedAuthority(
                        "ROLE_" + role.name()
                ));
    }

    private String decisionJson(
            String decision,
            int version,
            String note) {
        String noteValue = note == null ? "null" : "\"" + note + "\"";
        return """
                {
                  "decision": "%s",
                  "version": %d,
                  "note": %s
                }
                """.formatted(decision, version, noteValue);
    }

    private String taskStatus(Long taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM review_tasks WHERE id = ?",
                String.class,
                taskId
        );
    }

    private record Scenario(Long ownerId, Long taskId) {
    }
}
