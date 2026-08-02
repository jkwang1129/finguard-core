package com.finguard.core.review;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.entity.ReconciliationResult;
import com.finguard.core.reconciliation.mapper.ReconciliationResultMapper;
import com.finguard.core.reconciliation.model.ReconciliationMatchMethod;
import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.review.dto.ReviewDecisionRequest;
import com.finguard.core.review.dto.ReviewTaskQueryRequest;
import com.finguard.core.review.entity.ReviewTask;
import com.finguard.core.review.exception.InvalidReviewOperationException;
import com.finguard.core.review.mapper.ReviewTaskMapper;
import com.finguard.core.review.model.ReviewDecision;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.review.service.ReviewTaskService;
import com.finguard.core.risk.entity.RiskHit;
import com.finguard.core.risk.mapper.RiskHitMapper;
import com.finguard.core.risk.model.RiskReasonCode;
import com.finguard.core.risk.model.RiskRuleCode;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewTaskWorkflowIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 2, 13, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReconciliationResultMapper reconciliationResultMapper;
    @Autowired
    private RiskHitMapper riskHitMapper;
    @MockitoSpyBean
    private ReviewTaskMapper reviewTaskMapper;
    @Autowired
    private ReviewTaskService reviewTaskService;
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
    void queryProjectsBothSourcesWithStableFiltersAndOrder() {
        Scenario scenario = createScenario();
        jdbcTemplate.update(
                "UPDATE review_tasks SET created_at = ? WHERE id IN (?, ?)",
                BASE_TIME.plusMinutes(1),
                scenario.exceptionTaskId(),
                scenario.riskTaskId()
        );

        var all = reviewTaskService.query(new ReviewTaskQueryRequest(
                null,
                null,
                ReviewTaskStatus.PENDING,
                null,
                null,
                null
        ));
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.records()).extracting(record -> record.id())
                .containsExactly(
                        scenario.riskTaskId(),
                        scenario.exceptionTaskId()
                );

        var exceptions = reviewTaskService.query(
                new ReviewTaskQueryRequest(
                        1L,
                        20L,
                        null,
                        null,
                        ReconciliationResultType.UNMATCHED,
                        null
                )
        );
        assertThat(exceptions.records()).singleElement()
                .satisfies(record -> {
                    assertThat(record.id())
                            .isEqualTo(scenario.exceptionTaskId());
                    assertThat(record.reconciliationResultId())
                            .isEqualTo(scenario.resultId());
                    assertThat(record.riskHitId()).isNull();
                    assertThat(record.ruleCode()).isNull();
                });

        var risks = reviewTaskService.query(
                new ReviewTaskQueryRequest(
                        1L,
                        20L,
                        null,
                        null,
                        null,
                        RiskRuleCode.LARGE_AMOUNT
                )
        );
        assertThat(risks.records()).singleElement()
                .satisfies(record -> {
                    assertThat(record.id())
                            .isEqualTo(scenario.riskTaskId());
                    assertThat(record.reconciliationResultId()).isNull();
                    assertThat(record.riskHitId())
                            .isEqualTo(scenario.riskHitId());
                    assertThat(record.csvTransactionId())
                            .isEqualTo(scenario.transactionId());
                    assertThat(record.reasonCode()).isEqualTo(
                            RiskReasonCode.AMOUNT_AT_OR_ABOVE_THRESHOLD
                    );
                });
    }

    @Test
    void decisionPersistsTerminalShapeAndRejectsRepeat() {
        Scenario scenario = createScenario();

        var response = reviewTaskService.decide(
                scenario.exceptionTaskId(),
                new ReviewDecisionRequest(
                        ReviewDecision.IGNORED,
                        0,
                        "  source checked  "
                ),
                scenario.ownerId()
        );

        assertThat(response.status()).isEqualTo(ReviewTaskStatus.IGNORED);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.reviewedBy()).isEqualTo(scenario.ownerId());
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(response.decisionNote()).isEqualTo("source checked");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM review_tasks WHERE id = ?",
                Integer.class,
                scenario.exceptionTaskId()
        )).isEqualTo(1);

        assertThatThrownBy(() -> reviewTaskService.decide(
                scenario.exceptionTaskId(),
                new ReviewDecisionRequest(
                        ReviewDecision.CONFIRMED,
                        1,
                        "overwrite"
                ),
                scenario.ownerId()
        )).isInstanceOf(InvalidReviewOperationException.class);
        assertThat(reviewTaskService.getById(
                scenario.exceptionTaskId()
        ).decisionNote()).isEqualTo("source checked");
    }

    @Test
    void responseReadbackFailureRollsBackDecision() {
        Scenario scenario = createScenario();
        doThrow(new IllegalStateException("injected readback failure"))
                .when(reviewTaskMapper)
                .selectTaskViewById(scenario.exceptionTaskId());

        assertThatThrownBy(() -> reviewTaskService.decide(
                scenario.exceptionTaskId(),
                new ReviewDecisionRequest(
                        ReviewDecision.CONFIRMED,
                        0,
                        "must roll back"
                ),
                scenario.ownerId()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("injected readback failure");

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT CONCAT(
                    status, '|', version, '|',
                    IF(reviewed_by IS NULL, 'NULL', 'SET'), '|',
                    IF(reviewed_at IS NULL, 'NULL', 'SET'), '|',
                    IF(decision_note IS NULL, 'NULL', 'SET')
                )
                FROM review_tasks
                WHERE id = ?
                """,
                String.class,
                scenario.exceptionTaskId()
        )).isEqualTo("PENDING|0|NULL|NULL|NULL");
    }

    @Test
    void concurrentConditionalUpdatesAllowExactlyOneWinner()
            throws Exception {
        Scenario scenario = createScenario();
        Long reviewerOne = fixture.insertUser();
        Long reviewerTwo = fixture.insertUser();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch startUpdates = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> updateInTransaction(
                    scenario.exceptionTaskId(),
                    reviewerOne,
                    ReviewTaskStatus.CONFIRMED,
                    "winner-one",
                    bothRead,
                    startUpdates
            ));
            Future<Integer> second = executor.submit(() -> updateInTransaction(
                    scenario.exceptionTaskId(),
                    reviewerTwo,
                    ReviewTaskStatus.IGNORED,
                    "winner-two",
                    bothRead,
                    startUpdates
            ));
            assertThat(bothRead.await(5, TimeUnit.SECONDS)).isTrue();
            startUpdates.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdownNow();
        }

        var persisted = reviewTaskService.getById(
                scenario.exceptionTaskId()
        );
        assertThat(persisted.status()).isIn(
                ReviewTaskStatus.CONFIRMED,
                ReviewTaskStatus.IGNORED
        );
        assertThat(persisted.version()).isEqualTo(1);
        assertThat(persisted.reviewedBy()).isIn(
                reviewerOne,
                reviewerTwo
        );
        assertThat(persisted.decisionNote()).isIn(
                "winner-one",
                "winner-two"
        );
        if (persisted.reviewedBy().equals(reviewerOne)) {
            assertThat(persisted.status())
                    .isEqualTo(ReviewTaskStatus.CONFIRMED);
            assertThat(persisted.decisionNote()).isEqualTo("winner-one");
        } else {
            assertThat(persisted.status())
                    .isEqualTo(ReviewTaskStatus.IGNORED);
            assertThat(persisted.decisionNote()).isEqualTo("winner-two");
        }
    }

    private int updateInTransaction(
            Long taskId,
            Long reviewerId,
            ReviewTaskStatus status,
            String note,
            CountDownLatch bothRead,
            CountDownLatch startUpdates) {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );
        Integer result = transaction.execute(transactionStatus -> {
            ReviewTask current = reviewTaskMapper.selectById(taskId);
            assertThat(current.getVersion()).isZero();
            bothRead.countDown();
            try {
                if (!startUpdates.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Concurrent update barrier timed out"
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return reviewTaskMapper.updateDecision(
                    taskId,
                    status,
                    0,
                    reviewerId,
                    BASE_TIME.plusHours(1),
                    note
            );
        });
        return result == null ? -1 : result;
    }

    private Scenario createScenario() {
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
                "REVIEW-WORKFLOW-" + importJobId,
                TransactionDirection.EXPENSE,
                "15000.00",
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

        RiskHit hit = new RiskHit();
        hit.setReconciliationResultId(result.getId());
        hit.setRuleCode(RiskRuleCode.LARGE_AMOUNT);
        hit.setReasonCode(
                RiskReasonCode.AMOUNT_AT_OR_ABOVE_THRESHOLD
        );
        hit.setObservedAmount(new BigDecimal("15000.00"));
        hit.setThresholdAmount(new BigDecimal("10000.00"));
        hit.setReasonSummary("Large amount review workflow evidence");
        assertThat(riskHitMapper.insertBatch(List.of(hit))).isEqualTo(1);
        hit = riskHitMapper.selectByReconciliationResultIds(
                List.of(result.getId())
        ).get(0);

        ReviewTask exceptionTask = new ReviewTask();
        exceptionTask.setSourceType(
                ReviewTaskSourceType.RECONCILIATION_EXCEPTION
        );
        exceptionTask.setReconciliationResultId(result.getId());
        exceptionTask.setStatus(ReviewTaskStatus.PENDING);
        exceptionTask.setVersion(0);
        ReviewTask riskTask = new ReviewTask();
        riskTask.setSourceType(ReviewTaskSourceType.RISK_HIT);
        riskTask.setRiskHitId(hit.getId());
        riskTask.setStatus(ReviewTaskStatus.PENDING);
        riskTask.setVersion(0);
        assertThat(reviewTaskMapper.insertBatch(
                List.of(exceptionTask, riskTask)
        )).isEqualTo(2);
        Long exceptionTaskId = reviewTaskMapper
                .selectByReconciliationResultIds(List.of(result.getId()))
                .get(0)
                .getId();
        Long riskTaskId = reviewTaskMapper
                .selectByRiskHitIds(List.of(hit.getId()))
                .get(0)
                .getId();
        return new Scenario(
                ownerId,
                transactionId,
                result.getId(),
                hit.getId(),
                exceptionTaskId,
                riskTaskId
        );
    }

    private record Scenario(
            Long ownerId,
            Long transactionId,
            Long resultId,
            Long riskHitId,
            Long exceptionTaskId,
            Long riskTaskId) {
    }
}
