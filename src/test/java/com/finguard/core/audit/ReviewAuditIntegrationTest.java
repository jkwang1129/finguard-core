package com.finguard.core.audit;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.review.dto.ReviewDecisionRequest;
import com.finguard.core.review.exception.InvalidReviewOperationException;
import com.finguard.core.review.exception.ReviewVersionConflictException;
import com.finguard.core.review.model.ReviewDecision;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.review.service.ReviewTaskService;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewAuditIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 3, 12, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReviewTaskService reviewTaskService;

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
    void confirmAndIgnoreShouldCreateSafeUserAudits() {
        Scenario confirmed = createScenario();
        Scenario ignored = createScenario();

        reviewTaskService.decide(
                confirmed.taskId(),
                new ReviewDecisionRequest(
                        ReviewDecision.CONFIRMED,
                        0,
                        "sensitive note must stay out of audit"
                ),
                confirmed.reviewerId()
        );
        reviewTaskService.decide(
                ignored.taskId(),
                new ReviewDecisionRequest(
                        ReviewDecision.IGNORED,
                        0,
                        "another private note"
                ),
                ignored.reviewerId()
        );

        assertAudit(
                confirmed,
                "REVIEW_CONFIRMED",
                "Review task confirmed"
        );
        assertAudit(
                ignored,
                "REVIEW_IGNORED",
                "Review task ignored"
        );
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE summary LIKE '%private%'
                   OR summary LIKE '%sensitive%'
                """,
                Integer.class
        )).isZero();
    }

    @Test
    void terminalReplayShouldNotCreateAnotherAudit() {
        Scenario scenario = createScenario();
        reviewTaskService.decide(
                scenario.taskId(),
                request(ReviewDecision.CONFIRMED),
                scenario.reviewerId()
        );

        assertThatThrownBy(() -> reviewTaskService.decide(
                scenario.taskId(),
                new ReviewDecisionRequest(
                        ReviewDecision.IGNORED,
                        1,
                        null
                ),
                scenario.reviewerId()
        )).isInstanceOf(InvalidReviewOperationException.class);
        assertThat(countAudit(scenario.taskId())).isEqualTo(1);
    }

    @Test
    void auditFailureShouldRollbackReviewDecision() {
        Scenario scenario = createScenario();
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    action_code,
                    actor_type,
                    actor_user_id,
                    initiated_by,
                    outcome,
                    review_task_id,
                    summary,
                    created_at
                ) VALUES (
                    'REVIEW_CONFIRMED',
                    'USER',
                    ?,
                    ?,
                    'SUCCESS',
                    ?,
                    'Review task confirmed',
                    ?
                )
                """,
                scenario.reviewerId(),
                scenario.reviewerId(),
                scenario.taskId(),
                BASE_TIME
        );

        assertThatThrownBy(() -> reviewTaskService.decide(
                scenario.taskId(),
                request(ReviewDecision.CONFIRMED),
                scenario.reviewerId()
        )).isInstanceOf(DuplicateKeyException.class);

        var rolledBack = reviewTaskService.getById(scenario.taskId());
        assertThat(rolledBack.status()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(rolledBack.version()).isZero();
        assertThat(rolledBack.reviewedBy()).isNull();
        assertThat(rolledBack.reviewedAt()).isNull();
        assertThat(rolledBack.decisionNote()).isNull();
        assertThat(countAudit(scenario.taskId())).isEqualTo(1);
    }

    @Test
    void concurrentDecisionsShouldLeaveOneDecisionAndOneAudit() {
        Scenario scenario = createScenario();
        Long secondReviewer = fixture.insertUser();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> decideResult(
                    scenario.taskId(),
                    scenario.reviewerId(),
                    ReviewDecision.CONFIRMED
            ));
            Future<Object> second = executor.submit(() -> decideResult(
                    scenario.taskId(),
                    secondReviewer,
                    ReviewDecision.IGNORED
            ));
            List<Object> outcomes = List.of(first.get(), second.get());

            assertThat(outcomes.stream().filter(
                    outcome -> !(outcome instanceof RuntimeException)
            )).hasSize(1);
            assertThat(outcomes.stream().filter(
                    outcome -> outcome instanceof ReviewVersionConflictException
                            || outcome
                            instanceof InvalidReviewOperationException
            )).hasSize(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } finally {
            executor.shutdownNow();
        }

        var persisted = reviewTaskService.getById(scenario.taskId());
        assertThat(persisted.status()).isIn(
                ReviewTaskStatus.CONFIRMED,
                ReviewTaskStatus.IGNORED
        );
        assertThat(persisted.version()).isEqualTo(1);
        assertThat(countAudit(scenario.taskId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT action_code, actor_user_id, initiated_by
                FROM audit_logs
                WHERE review_task_id = ?
                """,
                scenario.taskId()
        )).satisfies(row -> {
            String expectedAction = persisted.status()
                    == ReviewTaskStatus.CONFIRMED
                    ? "REVIEW_CONFIRMED"
                    : "REVIEW_IGNORED";
            assertThat(row.get("action_code")).isEqualTo(expectedAction);
            assertThat(row.get("actor_user_id"))
                    .isEqualTo(persisted.reviewedBy());
            assertThat(row.get("initiated_by"))
                    .isEqualTo(persisted.reviewedBy());
        });
    }

    private Object decideResult(
            Long taskId,
            Long reviewerId,
            ReviewDecision decision) {
        try {
            return reviewTaskService.decide(
                    taskId,
                    request(decision),
                    reviewerId
            );
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private ReviewDecisionRequest request(ReviewDecision decision) {
        return new ReviewDecisionRequest(decision, 0, null);
    }

    private void assertAudit(
            Scenario scenario,
            String actionCode,
            String summary) {
        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT action_code, actor_type, actor_user_id,
                       initiated_by, outcome, summary
                FROM audit_logs
                WHERE review_task_id = ?
                """,
                scenario.taskId()
        )).containsEntry("action_code", actionCode)
                .containsEntry("actor_type", "USER")
                .containsEntry("actor_user_id", scenario.reviewerId())
                .containsEntry("initiated_by", scenario.reviewerId())
                .containsEntry("outcome", "SUCCESS")
                .containsEntry("summary", summary);
    }

    private Scenario createScenario() {
        Long ownerId = fixture.insertUser();
        Long reviewerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        Long transactionId = fixture.insertTransaction(
                accountId,
                importJobId,
                "AUDIT-REVIEW-" + importJobId,
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
        Long jobId = jdbcTemplate.queryForObject(
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
                jobId,
                transactionId
        );
        Long resultId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM reconciliation_results
                WHERE reconciliation_job_id = ?
                """,
                Long.class,
                jobId
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
        Long taskId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM review_tasks
                WHERE reconciliation_result_id = ?
                """,
                Long.class,
                resultId
        );
        return new Scenario(reviewerId, taskId);
    }

    private int countAudit(Long reviewTaskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE review_task_id = ?",
                Integer.class,
                reviewTaskId
        );
        return count == null ? 0 : count;
    }

    private record Scenario(Long reviewerId, Long taskId) {
    }
}
