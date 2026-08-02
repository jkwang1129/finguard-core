package com.finguard.core.messaging.consumer.reconciliation;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.messaging.consumer.failure.ConsumerRetryPolicy;
import com.finguard.core.messaging.consumer.failure.ReliableConsumerForwarder;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.reconciliation.mapper.ReconciliationResultMapper;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;
import com.finguard.core.reconciliation.model.ReconciliationProcessingResult;
import com.finguard.core.reconciliation.service.ReconciliationJobService;
import com.finguard.core.reconciliation.service.impl.ReconciliationJobTransactionService;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.reconciliation.vo.ReconciliationJobResponse;
import com.finguard.core.review.mapper.ReviewTaskMapper;
import com.finguard.core.risk.mapper.RiskHitMapper;
import com.finguard.core.risk.rule.LargeAmountRule;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AsyncReconciliationProcessorIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 1, 9, 30);

    @Autowired
    private ReconciliationJobService reconciliationJobService;
    @Autowired
    private ReconciliationJobTransactionService transactionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private ReconciliationResultMapper reconciliationResultMapper;
    @MockitoSpyBean
    private ReviewTaskMapper reviewTaskMapper;
    @MockitoSpyBean
    private RiskHitMapper riskHitMapper;
    @MockitoSpyBean
    private LargeAmountRule largeAmountRule;

    private ReconciliationTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        reset(reconciliationResultMapper);
        reset(reviewTaskMapper);
        reset(riskHitMapper);
        reset(largeAmountRule);
        fixture.clean();
    }

    @Test
    void shouldCompleteAndIdempotentlyShortCircuitTerminalDelivery() {
        ReconciliationJobResponse accepted = createAcceptedJob();

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        ReconciliationJobResponse completed =
                reconciliationJobService.getById(accepted.id());
        assertThat(completed.status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertThat(completed.totalCount()).isEqualTo(1);
        assertThat(completed.matchedCount()).isEqualTo(1);
        assertThat(completed.unmatchedCount()).isZero();
        assertThat(completed.duplicateCount()).isZero();
        assertThat(completed.suspiciousCount()).isZero();
        assertThat(resultCount(accepted.id())).isEqualTo(1);
        assertThat(riskCount(accepted.id())).isZero();
        assertThat(reviewCount(accepted.id())).isZero();

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(
                        ReconciliationProcessingResult.ALREADY_COMPLETED
                );
        assertThat(resultCount(accepted.id())).isEqualTo(1);
        assertThat(riskCount(accepted.id())).isZero();
        assertThat(reviewCount(accepted.id())).isZero();
    }

    @Test
    void shouldPersistRulesAndReviewTasksIdempotently() {
        ReconciliationJobResponse accepted = createRiskJob();

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        ReconciliationJobResponse completed =
                reconciliationJobService.getById(accepted.id());
        assertThat(completed.status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertThat(completed.totalCount()).isEqualTo(5);
        assertThat(completed.unmatchedCount()).isEqualTo(5);
        assertThat(resultCount(accepted.id())).isEqualTo(5);
        assertThat(riskCount(accepted.id())).isEqualTo(10);
        assertThat(reviewCount(accepted.id())).isEqualTo(15);
        assertThat(pendingReviewCount(accepted.id())).isEqualTo(15);

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.ALREADY_COMPLETED);
        assertThat(resultCount(accepted.id())).isEqualTo(5);
        assertThat(riskCount(accepted.id())).isEqualTo(10);
        assertThat(reviewCount(accepted.id())).isEqualTo(15);
    }

    @Test
    void shouldRollbackResultsHitsAndTasksWhenTaskInsertFails() {
        ReconciliationJobResponse accepted = createRiskJob();
        doThrow(new DataAccessResourceFailureException("injected review failure"))
                .when(reviewTaskMapper).insertBatch(any());

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(reconciliationJobService.getById(accepted.id()).status())
                .isEqualTo(ReconciliationJobStatus.PENDING);
        assertThat(resultCount(accepted.id())).isZero();
        assertThat(riskCount(accepted.id())).isZero();
        assertThat(reviewCount(accepted.id())).isZero();

        reset(reviewTaskMapper);
        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        assertThat(resultCount(accepted.id())).isEqualTo(5);
        assertThat(riskCount(accepted.id())).isEqualTo(10);
        assertThat(reviewCount(accepted.id())).isEqualTo(15);
    }

    @Test
    void shouldRollbackAndRecoverWhenRuleEvaluationFails() {
        ReconciliationJobResponse accepted = createRiskJob();
        doThrow(new IllegalStateException("injected rule failure"))
                .when(largeAmountRule).evaluate(any());

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected rule failure");
        assertNoRiskPipelineEffects(accepted.id());

        reset(largeAmountRule);
        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        assertRiskPipelineCompleted(accepted.id());
    }

    @Test
    void shouldRollbackAndRecoverWhenRiskInsertFails() {
        ReconciliationJobResponse accepted = createRiskJob();
        doThrow(new DataAccessResourceFailureException("injected risk failure"))
                .when(riskHitMapper).insertBatch(any());

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertNoRiskPipelineEffects(accepted.id());

        reset(riskHitMapper);
        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        assertRiskPipelineCompleted(accepted.id());
    }

    @Test
    void shouldCountHistoricalCsvExpensesAcrossImportJobs() {
        ReconciliationJobResponse accepted = createCrossImportFrequencyJob();

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        assertThat(resultCount(accepted.id())).isEqualTo(1);
        assertThat(riskCount(accepted.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM risk_hits rh
                INNER JOIN reconciliation_results rr
                    ON rr.id = rh.reconciliation_result_id
                WHERE rr.reconciliation_job_id = ?
                  AND rh.rule_code = 'FREQUENT_TRANSACTION'
                  AND rh.observed_count = 5
                  AND rh.threshold_count = 5
                  AND rh.window_seconds = 600
                """,
                Integer.class,
                accepted.id()
        )).isEqualTo(1);
        assertThat(reviewCount(accepted.id())).isEqualTo(2);
    }

    @Test
    void shouldRollbackTransientFailureAndRecoverOnNextAttempt() {
        ReconciliationJobResponse accepted = createAcceptedJob();
        doThrow(new DataAccessResourceFailureException("injected failure"))
                .when(reconciliationResultMapper).insertBatch(any());

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(reconciliationJobService.getById(accepted.id()).status())
                .isEqualTo(ReconciliationJobStatus.PENDING);
        assertThat(resultCount(accepted.id())).isZero();

        reset(reconciliationResultMapper);
        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        assertThat(reconciliationJobService.getById(accepted.id()).status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertThat(resultCount(accepted.id())).isEqualTo(1);
    }

    @Test
    void shouldSerializeConcurrentMessagesByJobRowLock()
            throws Exception {
        ReconciliationJobResponse accepted = createRiskJob();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ReconciliationProcessingResult> first =
                    executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return transactionService.processPending(
                                accepted.id()
                        );
                    });
            Future<ReconciliationProcessingResult> second =
                    executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return transactionService.processPending(
                                accepted.id()
                        );
                    });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(
                    ReconciliationProcessingResult.PROCESSED,
                    ReconciliationProcessingResult.ALREADY_COMPLETED
            );
        } finally {
            executor.shutdownNow();
        }

        assertRiskPipelineCompleted(accepted.id());
        assertThat(reconciliationJobService.getById(accepted.id()).status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
    }

    @Test
    void shouldRecoverLegacyProcessingAndFailOnlyNonTerminalJob() {
        ReconciliationJobResponse recoverable = createAcceptedJob();
        transactionService.markProcessing(recoverable.id());
        assertThat(transactionService.processPending(recoverable.id()))
                .isEqualTo(ReconciliationProcessingResult.RECOVERED);
        assertThat(reconciliationJobService.getById(recoverable.id()).status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);

        ReconciliationJobResponse exhausted = createAcceptedJob();
        assertThat(transactionService.markRetryExhausted(exhausted.id()))
                .isTrue();
        ReconciliationJobResponse failed =
                reconciliationJobService.getById(exhausted.id());
        assertThat(failed.status()).isEqualTo(ReconciliationJobStatus.FAILED);
        assertThat(failed.errorSummary())
                .isEqualTo("Reconciliation retry limit reached");
        assertThat(transactionService.markRetryExhausted(exhausted.id()))
                .isFalse();
        assertThat(transactionService.markRetryExhausted(recoverable.id()))
                .isFalse();
    }

    @Test
    void shouldShortCircuitRedeliveryAfterAckLoss() throws Exception {
        ReconciliationJobResponse accepted = createRiskJob();
        ReconciliationJobMessageListener listener =
                new ReconciliationJobMessageListener(
                        new ReconciliationJobMessageHandler(
                                transactionService
                        ),
                        new ConsumerRetryPolicy(),
                        mock(ReliableConsumerForwarder.class)
                );
        JobRequestedMessage message = new JobRequestedMessage(
                "outbox-9301",
                OutboxEventType.RECONCILIATION_REQUESTED,
                accepted.id(),
                1,
                OffsetDateTime.of(
                        2026, 8, 1, 10, 0, 0, 0,
                        ZoneOffset.ofHours(8)
                )
        );
        Message amqpMessage = new Message(new byte[0]);
        Channel failedChannel = mock(Channel.class);
        doThrow(new IOException("injected ACK failure"))
                .when(failedChannel).basicAck(81L, false);

        assertThatThrownBy(() -> listener.onMessage(
                message,
                amqpMessage,
                failedChannel,
                81L
        )).isInstanceOf(IOException.class)
                .hasMessage("injected ACK failure");
        assertThat(reconciliationJobService.getById(accepted.id()).status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertRiskPipelineCompleted(accepted.id());

        Channel recoveredChannel = mock(Channel.class);
        listener.onMessage(message, amqpMessage, recoveredChannel, 82L);

        verify(recoveredChannel).basicAck(82L, false);
        assertRiskPipelineCompleted(accepted.id());
    }

    private ReconciliationJobResponse createAcceptedJob() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        String externalNo = "ASYNC-" + importJobId;
        fixture.insertTransaction(
                accountId,
                importJobId,
                externalNo,
                TransactionDirection.INCOME,
                "88.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        fixture.insertTransaction(
                accountId,
                null,
                externalNo,
                TransactionDirection.INCOME,
                "88.00",
                BASE_TIME,
                TransactionSource.MANUAL
        );
        return reconciliationJobService.create(importJobId, ownerId);
    }

    private ReconciliationJobResponse createRiskJob() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                5
        );
        for (int index = 0; index < 5; index++) {
            fixture.insertTransaction(
                    accountId,
                    importJobId,
                    "RISK-" + importJobId + "-" + index,
                    TransactionDirection.EXPENSE,
                    "15000.00",
                    BASE_TIME.minusMinutes(4L - index),
                    TransactionSource.CSV_IMPORT
            );
        }
        return reconciliationJobService.create(importJobId, ownerId);
    }

    private ReconciliationJobResponse createCrossImportFrequencyJob() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long historicalImportJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                4
        );
        for (int index = 0; index < 4; index++) {
            fixture.insertTransaction(
                    accountId,
                    historicalImportJobId,
                    "HISTORY-" + historicalImportJobId + "-" + index,
                    TransactionDirection.EXPENSE,
                    Integer.toString(index + 1),
                    BASE_TIME.minusMinutes(8L - index),
                    TransactionSource.CSV_IMPORT
            );
        }
        Long currentImportJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        fixture.insertTransaction(
                accountId,
                currentImportJobId,
                "CURRENT-" + currentImportJobId,
                TransactionDirection.EXPENSE,
                "5.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        return reconciliationJobService.create(currentImportJobId, ownerId);
    }

    private void assertNoRiskPipelineEffects(Long reconciliationJobId) {
        assertThat(reconciliationJobService.getById(reconciliationJobId)
                .status()).isEqualTo(ReconciliationJobStatus.PENDING);
        assertThat(resultCount(reconciliationJobId)).isZero();
        assertThat(riskCount(reconciliationJobId)).isZero();
        assertThat(reviewCount(reconciliationJobId)).isZero();
    }

    private void assertRiskPipelineCompleted(Long reconciliationJobId) {
        assertThat(reconciliationJobService.getById(reconciliationJobId)
                .status()).isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertThat(resultCount(reconciliationJobId)).isEqualTo(5);
        assertThat(riskCount(reconciliationJobId)).isEqualTo(10);
        assertThat(reviewCount(reconciliationJobId)).isEqualTo(15);
    }

    private int resultCount(Long reconciliationJobId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_results "
                        + "WHERE reconciliation_job_id = ?",
                Integer.class,
                reconciliationJobId
        );
    }

    private int riskCount(Long reconciliationJobId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM risk_hits rh
                INNER JOIN reconciliation_results rr
                    ON rr.id = rh.reconciliation_result_id
                WHERE rr.reconciliation_job_id = ?
                """,
                Integer.class,
                reconciliationJobId
        );
    }

    private int reviewCount(Long reconciliationJobId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_tasks rt
                LEFT JOIN reconciliation_results rr
                    ON rr.id = rt.reconciliation_result_id
                LEFT JOIN risk_hits rh ON rh.id = rt.risk_hit_id
                LEFT JOIN reconciliation_results hit_rr
                    ON hit_rr.id = rh.reconciliation_result_id
                WHERE COALESCE(
                    rr.reconciliation_job_id,
                    hit_rr.reconciliation_job_id
                ) = ?
                """,
                Integer.class,
                reconciliationJobId
        );
    }

    private int pendingReviewCount(Long reconciliationJobId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_tasks rt
                LEFT JOIN reconciliation_results rr
                    ON rr.id = rt.reconciliation_result_id
                LEFT JOIN risk_hits rh ON rh.id = rt.risk_hit_id
                LEFT JOIN reconciliation_results hit_rr
                    ON hit_rr.id = rh.reconciliation_result_id
                WHERE COALESCE(
                    rr.reconciliation_job_id,
                    hit_rr.reconciliation_job_id
                ) = ?
                  AND rt.status = 'PENDING'
                  AND rt.version = 0
                  AND rt.reviewed_by IS NULL
                  AND rt.reviewed_at IS NULL
                """,
                Integer.class,
                reconciliationJobId
        );
    }
}
