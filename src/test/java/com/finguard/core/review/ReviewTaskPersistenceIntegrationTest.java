package com.finguard.core.review;

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
import com.finguard.core.review.service.ReviewTaskGenerator;
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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewTaskPersistenceIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 2, 11, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReconciliationResultMapper reconciliationResultMapper;
    @Autowired
    private RiskHitMapper riskHitMapper;
    @Autowired
    private ReviewTaskMapper reviewTaskMapper;
    @Autowired
    private ReviewTaskGenerator generator;
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
    void shouldGenerateExceptionAndRiskTasksWithStableReadback() {
        Scenario scenario = createScenario();
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        List<ReviewTask> tasks = transaction.execute(status ->
                generator.generateAndPersist(
                        List.of(scenario.result()),
                        List.of(scenario.hit())
                )
        );

        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(ReviewTask::getSourceType)
                .containsExactly(
                        ReviewTaskSourceType.RECONCILIATION_EXCEPTION,
                        ReviewTaskSourceType.RISK_HIT
                );
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.getStatus())
                    .isEqualTo(ReviewTaskStatus.PENDING);
            assertThat(task.getVersion()).isZero();
            assertThat(task.getReviewedBy()).isNull();
            assertThat(task.getReviewedAt()).isNull();
            assertThat(task.getDecisionNote()).isNull();
        });
        assertThat(reviewTaskMapper.selectByReconciliationResultIds(
                List.of(scenario.result().getId())
        )).hasSize(1);
        assertThat(reviewTaskMapper.selectByRiskHitIds(
                List.of(scenario.hit().getId())
        )).hasSize(1);
    }

    @Test
    void shouldRejectDuplicateUnknownAndInvalidShapes() {
        Scenario scenario = createScenario();
        insertExceptionTask(scenario.result().getId());

        assertThatThrownBy(() ->
                insertExceptionTask(scenario.result().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO review_tasks (
                    source_type,
                    reconciliation_result_id,
                    status,
                    version
                ) VALUES ('RECONCILIATION_EXCEPTION', ?, 'PENDING', 0)
                """,
                Long.MAX_VALUE
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO review_tasks (
                    source_type,
                    reconciliation_result_id,
                    risk_hit_id,
                    status,
                    version
                ) VALUES ('RISK_HIT', ?, ?, 'PENDING', 0)
                """,
                scenario.result().getId(),
                scenario.hit().getId()
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO review_tasks (
                    source_type,
                    risk_hit_id,
                    status,
                    version
                ) VALUES ('RISK_HIT', ?, 'CONFIRMED', 0)
                """,
                scenario.hit().getId()
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void shouldRestrictDeletingReviewSources() {
        Scenario scenario = createScenario();
        ReviewTask riskTask = new ReviewTask();
        riskTask.setSourceType(ReviewTaskSourceType.RISK_HIT);
        riskTask.setRiskHitId(scenario.hit().getId());
        riskTask.setStatus(ReviewTaskStatus.PENDING);
        riskTask.setVersion(0);
        assertThat(reviewTaskMapper.insertBatch(List.of(riskTask)))
                .isEqualTo(1);

        assertThatThrownBy(() -> riskHitMapper.deleteById(
                scenario.hit().getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
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
                "REVIEW-" + importJobId,
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
        hit.setReasonSummary("Large amount integration evidence");
        assertThat(riskHitMapper.insertBatch(List.of(hit))).isEqualTo(1);
        hit = riskHitMapper.selectByReconciliationResultIds(
                List.of(result.getId())
        ).get(0);
        return new Scenario(result, hit);
    }

    private void insertExceptionTask(Long resultId) {
        ReviewTask task = new ReviewTask();
        task.setSourceType(
                ReviewTaskSourceType.RECONCILIATION_EXCEPTION
        );
        task.setReconciliationResultId(resultId);
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setVersion(0);
        reviewTaskMapper.insertBatch(List.of(task));
    }

    private record Scenario(
            ReconciliationResult result,
            RiskHit hit) {
    }
}
