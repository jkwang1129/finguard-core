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

    private ReconciliationTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        reset(reconciliationResultMapper);
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

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(
                        ReconciliationProcessingResult.ALREADY_COMPLETED
                );
        assertThat(resultCount(accepted.id())).isEqualTo(1);
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
        ReconciliationJobResponse accepted = createAcceptedJob();
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

        assertThat(resultCount(accepted.id())).isEqualTo(1);
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
        ReconciliationJobResponse accepted = createAcceptedJob();
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
        assertThat(resultCount(accepted.id())).isEqualTo(1);

        Channel recoveredChannel = mock(Channel.class);
        listener.onMessage(message, amqpMessage, recoveredChannel, 82L);

        verify(recoveredChannel).basicAck(82L, false);
        assertThat(resultCount(accepted.id())).isEqualTo(1);
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

    private int resultCount(Long reconciliationJobId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_results "
                        + "WHERE reconciliation_job_id = ?",
                Integer.class,
                reconciliationJobId
        );
    }
}
