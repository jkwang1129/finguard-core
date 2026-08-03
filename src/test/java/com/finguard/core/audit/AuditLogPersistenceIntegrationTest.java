package com.finguard.core.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.audit.dto.AuditLogQueryRequest;
import com.finguard.core.audit.mapper.AuditLogMapper;
import com.finguard.core.audit.model.AuditActionCode;
import com.finguard.core.audit.model.AuditActorType;
import com.finguard.core.audit.model.AuditOutcome;
import com.finguard.core.audit.model.ReconciliationAuditCounts;
import com.finguard.core.audit.service.AuditLogService;
import com.finguard.core.audit.vo.AuditLogResponse;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.review.model.ReviewDecision;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditLogPersistenceIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 3, 10, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private PlatformTransactionManager transactionManager;

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
    void shouldPersistFiveTypedEventsAndQuerySafely() {
        Scenario scenario = createScenario();
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            auditLogService.recordCsvUploadAccepted(
                    scenario.importJobId(),
                    scenario.ownerId()
            );
            auditLogService.recordImportFailed(
                    scenario.failedImportJobId(),
                    scenario.ownerId()
            );
            auditLogService.recordReconciliationCompleted(
                    scenario.reconciliationJobId(),
                    scenario.ownerId(),
                    new ReconciliationAuditCounts(1, 0, 1, 0, 0)
            );
            auditLogService.recordReviewDecision(
                    scenario.reviewTaskId(),
                    scenario.ownerId(),
                    ReviewDecision.CONFIRMED
            );
            auditLogService.recordReviewDecision(
                    scenario.reviewTaskId(),
                    scenario.ownerId(),
                    ReviewDecision.IGNORED
            );
        });

        PageResponse<AuditLogResponse> page = auditLogService.query(
                new AuditLogQueryRequest(
                        1L,
                        20L,
                        null,
                        scenario.ownerId()
                )
        );

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.records())
                .extracting(AuditLogResponse::actionCode)
                .containsExactlyInAnyOrder(AuditActionCode.values());
        assertThat(page.records()).filteredOn(event ->
                        event.actionCode()
                                == AuditActionCode.IMPORT_FAILED)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.actorType())
                            .isEqualTo(AuditActorType.SYSTEM);
                    assertThat(event.actorUserId()).isNull();
                    assertThat(event.outcome())
                            .isEqualTo(AuditOutcome.FAILED);
                    assertThat(event.summary()).isEqualTo("Import failed");
                });
        assertThat(page.records()).filteredOn(event ->
                        event.actionCode()
                                == AuditActionCode.RECONCILIATION_COMPLETED)
                .singleElement()
                .extracting(AuditLogResponse::summary)
                .isEqualTo(
                        "Reconciliation completed: total=1, matched=0, "
                                + "unmatched=1, duplicate=0, suspicious=0"
                );
        assertThat(BaseMapper.class.isAssignableFrom(
                AuditLogMapper.class
        )).isFalse();
    }

    @Test
    void writeMethodsShouldRequireAnExistingTransaction() {
        Long ownerId = fixture.insertUser();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );

        assertThatThrownBy(() ->
                auditLogService.recordCsvUploadAccepted(
                        importJobId,
                        ownerId
                ))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(countAuditLogs(ownerId)).isZero();
    }

    @Test
    void databaseShouldRejectDuplicateInvalidAndUnknownShapes() {
        Long ownerId = fixture.insertUser();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        insertRaw(
                "CSV_UPLOAD_ACCEPTED",
                "USER",
                ownerId,
                ownerId,
                "SUCCESS",
                importJobId,
                null,
                null,
                "CSV upload accepted"
        );

        assertThatThrownBy(() -> insertRaw(
                "CSV_UPLOAD_ACCEPTED",
                "USER",
                ownerId,
                ownerId,
                "SUCCESS",
                importJobId,
                null,
                null,
                "CSV upload accepted"
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRaw(
                "IMPORT_FAILED",
                "SYSTEM",
                ownerId,
                ownerId,
                "FAILED",
                importJobId,
                null,
                null,
                "Import failed"
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRaw(
                "CSV_UPLOAD_ACCEPTED",
                "USER",
                ownerId,
                ownerId,
                "FAILED",
                importJobId,
                null,
                null,
                "CSV upload accepted"
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRaw(
                "RECONCILIATION_COMPLETED",
                "SYSTEM",
                null,
                ownerId,
                "SUCCESS",
                importJobId,
                null,
                null,
                "Reconciliation completed"
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRaw(
                "CSV_UPLOAD_ACCEPTED",
                "USER",
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                "SUCCESS",
                importJobId,
                null,
                null,
                "CSV upload accepted"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void auditForeignKeysShouldRestrictDeletingEvidence() {
        Long ownerId = fixture.insertUser();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> auditLogService.recordCsvUploadAccepted(
                        importJobId,
                        ownerId
                )
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM import_jobs WHERE id = ?",
                importJobId
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                ownerId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Scenario createScenario() {
        Long ownerId = fixture.insertUser();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        Long failedImportJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.FAILED,
                0
        );
        Long accountId = fixture.insertAccount();
        Long transactionId = fixture.insertTransaction(
                accountId,
                importJobId,
                "AUDIT-" + importJobId,
                TransactionDirection.EXPENSE,
                "100.00",
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
        Long reconciliationJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM reconciliation_jobs WHERE import_job_id = ?",
                Long.class,
                importJobId
        );
        jdbcTemplate.update(
                """
                INSERT INTO reconciliation_results (
                    reconciliation_job_id,
                    csv_transaction_id,
                    result_type,
                    match_method,
                    reason_code
                ) VALUES (?, ?, 'UNMATCHED', 'NONE', 'NO_CANDIDATE')
                """,
                reconciliationJobId,
                transactionId
        );
        Long resultId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM reconciliation_results
                WHERE reconciliation_job_id = ?
                """,
                Long.class,
                reconciliationJobId
        );
        jdbcTemplate.update(
                """
                INSERT INTO review_tasks (
                    source_type,
                    reconciliation_result_id,
                    status,
                    version
                ) VALUES ('RECONCILIATION_EXCEPTION', ?, 'PENDING', 0)
                """,
                resultId
        );
        Long reviewTaskId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM review_tasks
                WHERE reconciliation_result_id = ?
                """,
                Long.class,
                resultId
        );
        return new Scenario(
                ownerId,
                importJobId,
                failedImportJobId,
                reconciliationJobId,
                reviewTaskId
        );
    }

    private void insertRaw(
            String actionCode,
            String actorType,
            Long actorUserId,
            Long initiatedBy,
            String outcome,
            Long importJobId,
            Long reconciliationJobId,
            Long reviewTaskId,
            String summary) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    action_code,
                    actor_type,
                    actor_user_id,
                    initiated_by,
                    outcome,
                    import_job_id,
                    reconciliation_job_id,
                    review_task_id,
                    summary,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                actionCode,
                actorType,
                actorUserId,
                initiatedBy,
                outcome,
                importJobId,
                reconciliationJobId,
                reviewTaskId,
                summary,
                BASE_TIME
        );
    }

    private int countAuditLogs(Long initiatedBy) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE initiated_by = ?",
                Integer.class,
                initiatedBy
        );
        return count == null ? 0 : count;
    }

    private record Scenario(
            Long ownerId,
            Long importJobId,
            Long failedImportJobId,
            Long reconciliationJobId,
            Long reviewTaskId) {
    }
}
