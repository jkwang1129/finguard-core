package com.finguard.core.audit;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.importjob.model.ImportProcessingResult;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.service.impl.ImportJobTransactionService;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ImportAuditIntegrationTest {

    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ImportJobService importJobService;
    @Autowired
    private ImportJobTransactionService transactionService;

    private ReconciliationTestFixture fixture;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
        ownerId = fixture.insertUser();
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void firstUploadShouldAuditOnceAndDuplicateShouldReuseEvidence() {
        byte[] content = invalidHeaderContent();

        ImportJobResponse accepted = importJobService.upload(
                "accepted.csv",
                content,
                ownerId
        );
        ImportJobResponse duplicate = importJobService.upload(
                "duplicate-name.csv",
                content,
                ownerId
        );

        assertThat(accepted.duplicateFile()).isFalse();
        assertThat(duplicate.duplicateFile()).isTrue();
        assertThat(duplicate.id()).isEqualTo(accepted.id());
        assertThat(countAction(
                accepted.id(),
                "CSV_UPLOAD_ACCEPTED"
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT actor_type, actor_user_id, initiated_by,
                       outcome, summary
                FROM audit_logs
                WHERE action_code = 'CSV_UPLOAD_ACCEPTED'
                  AND import_job_id = ?
                """,
                accepted.id()
        )).containsEntry("actor_type", "USER")
                .containsEntry("actor_user_id", ownerId)
                .containsEntry("initiated_by", ownerId)
                .containsEntry("outcome", "SUCCESS")
                .containsEntry("summary", "CSV upload accepted");
    }

    @Test
    void parseFailureAndTerminalReplayShouldAuditOnce() {
        ImportJobResponse accepted = upload(invalidHeaderContent());

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ImportProcessingResult.PROCESSED);
        ImportJobResponse failed = importJobService.getById(accepted.id());

        assertThat(failed.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ImportProcessingResult.ALREADY_COMPLETED);
        assertFailedAudit(accepted.id());
    }

    @Test
    void allRowsFailedShouldCreateFailedAudit() {
        String row = "MISSING-ACCOUNT," + token()
                + ",EXPENSE,1.00,2026-08-03 10:00:00,test";
        ImportJobResponse accepted = upload(
                (HEADER + "\n" + row + "\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ImportProcessingResult.PROCESSED);

        ImportJobResponse failed = importJobService.getById(accepted.id());
        assertThat(failed.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(failed.failedRows()).isEqualTo(1);
        assertFailedAudit(accepted.id());
    }

    @Test
    void processingFailureAndRetryExhaustionShouldAuditOnlyChanges() {
        ImportJobResponse processingFailure = upload(
                invalidHeaderContent()
        );
        transactionService.markProcessing(processingFailure.id());
        transactionService.markProcessingFailed(processingFailure.id());
        assertFailedAudit(processingFailure.id());

        ImportJobResponse retryExhaustion = upload(
                ("wrong-" + token()).getBytes(StandardCharsets.UTF_8)
        );
        assertThat(transactionService.markRetryExhausted(
                retryExhaustion.id()
        )).isTrue();
        assertThat(transactionService.markRetryExhausted(
                retryExhaustion.id()
        )).isFalse();
        assertFailedAudit(retryExhaustion.id());
    }

    @Test
    void auditFailureShouldRollbackImportFailureTransition() {
        ImportJobResponse accepted = upload(invalidHeaderContent());
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    action_code,
                    actor_type,
                    initiated_by,
                    outcome,
                    import_job_id,
                    summary,
                    created_at
                ) VALUES (
                    'IMPORT_FAILED',
                    'SYSTEM',
                    ?,
                    'FAILED',
                    ?,
                    'Import failed',
                    ?
                )
                """,
                ownerId,
                accepted.id(),
                LocalDateTime.of(2026, 8, 3, 10, 0)
        );

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(DuplicateKeyException.class);

        ImportJobResponse rolledBack = importJobService.getById(
                accepted.id()
        );
        assertThat(rolledBack.status()).isEqualTo(ImportJobStatus.PENDING);
        assertThat(rolledBack.startedAt()).isNull();
        assertThat(rolledBack.finishedAt()).isNull();
        assertThat(rolledBack.fileErrorCode()).isNull();
        assertThat(countAction(accepted.id(), "IMPORT_FAILED"))
                .isEqualTo(1);
    }

    private ImportJobResponse upload(byte[] content) {
        return importJobService.upload(
                "audit-" + token() + ".csv",
                content,
                ownerId
        );
    }

    private byte[] invalidHeaderContent() {
        return ("wrong_header\n" + token() + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private void assertFailedAudit(Long importJobId) {
        assertThat(countAction(importJobId, "IMPORT_FAILED"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT actor_type, actor_user_id, initiated_by,
                       outcome, summary
                FROM audit_logs
                WHERE action_code = 'IMPORT_FAILED'
                  AND import_job_id = ?
                """,
                importJobId
        )).containsEntry("actor_type", "SYSTEM")
                .containsEntry("actor_user_id", null)
                .containsEntry("initiated_by", ownerId)
                .containsEntry("outcome", "FAILED")
                .containsEntry("summary", "Import failed");
    }

    private int countAction(Long importJobId, String actionCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE import_job_id = ?
                  AND action_code = ?
                """,
                Integer.class,
                importJobId,
                actionCode
        );
        return count == null ? 0 : count;
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
