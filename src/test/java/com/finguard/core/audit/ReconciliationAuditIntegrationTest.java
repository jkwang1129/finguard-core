package com.finguard.core.audit;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;
import com.finguard.core.reconciliation.model.ReconciliationProcessingResult;
import com.finguard.core.reconciliation.service.ReconciliationJobService;
import com.finguard.core.reconciliation.service.impl.ReconciliationJobTransactionService;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.reconciliation.vo.ReconciliationJobResponse;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReconciliationAuditIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 3, 11, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReconciliationJobService reconciliationJobService;
    @Autowired
    private ReconciliationJobTransactionService transactionService;

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
    void completedReconciliationAndReplayShouldAuditOnce() {
        Scenario scenario = createScenario("100.00");
        ReconciliationJobResponse accepted =
                reconciliationJobService.create(
                        scenario.importJobId(),
                        scenario.ownerId()
                );

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(
                        ReconciliationProcessingResult.ALREADY_COMPLETED
                );

        ReconciliationJobResponse completed =
                reconciliationJobService.getById(accepted.id());
        assertThat(completed.status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertThat(completed.totalCount()).isEqualTo(1);
        assertThat(completed.unmatchedCount()).isEqualTo(1);
        assertThat(countAudit(accepted.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT actor_type, actor_user_id, initiated_by,
                       outcome, summary
                FROM audit_logs
                WHERE action_code = 'RECONCILIATION_COMPLETED'
                  AND reconciliation_job_id = ?
                """,
                accepted.id()
        )).containsEntry("actor_type", "SYSTEM")
                .containsEntry("actor_user_id", null)
                .containsEntry("initiated_by", scenario.ownerId())
                .containsEntry("outcome", "SUCCESS")
                .containsEntry(
                        "summary",
                        "Reconciliation completed: total=1, matched=0, "
                                + "unmatched=1, duplicate=0, suspicious=0"
                );
    }

    @Test
    void auditFailureShouldRollbackResultsRisksTasksAndCompletion() {
        Scenario scenario = createScenario("20000.00");
        ReconciliationJobResponse accepted =
                reconciliationJobService.create(
                        scenario.importJobId(),
                        scenario.ownerId()
                );
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    action_code,
                    actor_type,
                    initiated_by,
                    outcome,
                    reconciliation_job_id,
                    summary,
                    created_at
                ) VALUES (
                    'RECONCILIATION_COMPLETED',
                    'SYSTEM',
                    ?,
                    'SUCCESS',
                    ?,
                    'Reconciliation completed: seeded conflict',
                    ?
                )
                """,
                scenario.ownerId(),
                accepted.id(),
                BASE_TIME
        );

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(DuplicateKeyException.class);

        ReconciliationJobResponse rolledBack =
                reconciliationJobService.getById(accepted.id());
        assertThat(rolledBack.status())
                .isEqualTo(ReconciliationJobStatus.PENDING);
        assertThat(rolledBack.startedAt()).isNull();
        assertThat(rolledBack.finishedAt()).isNull();
        assertThat(countRows(
                "reconciliation_results",
                "reconciliation_job_id",
                accepted.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM risk_hits rh
                INNER JOIN reconciliation_results rr
                  ON rr.id = rh.reconciliation_result_id
                WHERE rr.reconciliation_job_id = ?
                """,
                Integer.class,
                accepted.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_tasks rt
                LEFT JOIN reconciliation_results direct_result
                  ON direct_result.id = rt.reconciliation_result_id
                LEFT JOIN risk_hits rh ON rh.id = rt.risk_hit_id
                LEFT JOIN reconciliation_results risk_result
                  ON risk_result.id = rh.reconciliation_result_id
                WHERE direct_result.reconciliation_job_id = ?
                   OR risk_result.reconciliation_job_id = ?
                """,
                Integer.class,
                accepted.id(),
                accepted.id()
        )).isZero();
        assertThat(countAudit(accepted.id())).isEqualTo(1);
    }

    private Scenario createScenario(String amount) {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        fixture.insertTransaction(
                accountId,
                importJobId,
                "AUDIT-RECON-" + UUID.randomUUID(),
                TransactionDirection.EXPENSE,
                amount,
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        return new Scenario(ownerId, importJobId);
    }

    private int countAudit(Long reconciliationJobId) {
        return countRows(
                "audit_logs",
                "reconciliation_job_id",
                reconciliationJobId
        );
    }

    private int countRows(String table, String column, Long id) {
        String allowed;
        if ("audit_logs".equals(table)
                && "reconciliation_job_id".equals(column)) {
            allowed = "SELECT COUNT(*) FROM audit_logs "
                    + "WHERE reconciliation_job_id = ?";
        } else if ("reconciliation_results".equals(table)
                && "reconciliation_job_id".equals(column)) {
            allowed = "SELECT COUNT(*) FROM reconciliation_results "
                    + "WHERE reconciliation_job_id = ?";
        } else {
            throw new IllegalArgumentException("Unsupported count target");
        }
        Integer count = jdbcTemplate.queryForObject(
                allowed,
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }

    private record Scenario(Long ownerId, Long importJobId) {
    }
}
