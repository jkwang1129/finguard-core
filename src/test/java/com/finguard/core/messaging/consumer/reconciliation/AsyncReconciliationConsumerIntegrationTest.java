package com.finguard.core.messaging.consumer.reconciliation;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import com.finguard.core.messaging.consumer.failure.ReliableConsumerForwarder;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;
import com.finguard.core.reconciliation.service.ReconciliationJobService;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.reconciliation.vo.ReconciliationJobResponse;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "finguard.messaging.reconciliation-consumer.enabled=true",
                "finguard.messaging.reconciliation-consumer.concurrency=2",
                "finguard.messaging.outbox.enabled=false"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AsyncReconciliationConsumerIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 1, 9, 30);

    @Autowired
    private ReconciliationJobService reconciliationJobService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ReconciliationJobMessageHandler messageHandler;

    private ReconciliationTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        purgeQueues();
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        reset(messageHandler);
        purgeQueues();
        fixture.clean();
    }

    @Test
    void shouldConsumeConcurrentDuplicateMessagesOnlyOnce()
            throws Exception {
        ReconciliationJobResponse accepted = createAcceptedJob();
        JobRequestedMessage message = requestedMessage(
                accepted.id(),
                "outbox-9201"
        );

        publish(message);
        publish(message);

        ReconciliationJobResponse completed = waitForTerminal(
                accepted.id(),
                10_000
        );
        assertThat(completed.status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertThat(completed.totalCount()).isEqualTo(1);
        assertThat(completed.matchedCount()).isEqualTo(1);
        assertThat(resultCount(accepted.id())).isEqualTo(1);
        waitForMainQueueToDrain();
    }

    @Test
    void shouldDelayOneTransientFailureThenRecover()
            throws Exception {
        ReconciliationJobResponse accepted = createAcceptedJob();
        doThrow(new IllegalStateException("injected transient failure"))
                .doCallRealMethod()
                .when(messageHandler).handle(any());

        long started = System.nanoTime();
        publish(requestedMessage(accepted.id(), "outbox-9202"));

        ReconciliationJobResponse completed = waitForTerminal(
                accepted.id(),
                15_000
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started
        );
        assertThat(completed.status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(4_500);
        assertThat(resultCount(accepted.id())).isEqualTo(1);
        assertThat(queueMessageCount(
                RabbitMessagingConfiguration.RECONCILIATION_DLQ
        )).isZero();
    }

    @Test
    void shouldExhaustTwoRetriesThenFailAndDeadLetter()
            throws Exception {
        ReconciliationJobResponse accepted = createAcceptedJob();
        doThrow(new IllegalStateException("injected persistent failure"))
                .when(messageHandler).handle(any());

        long started = System.nanoTime();
        publish(requestedMessage(accepted.id(), "outbox-9203"));

        ReconciliationJobResponse failed = waitForTerminal(
                accepted.id(),
                45_000
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started
        );
        assertThat(failed.status())
                .isEqualTo(ReconciliationJobStatus.FAILED);
        assertThat(failed.errorSummary())
                .isEqualTo("Reconciliation retry limit reached");
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(34_000);
        assertThat(resultCount(accepted.id())).isZero();

        Message deadLetter = rabbitTemplate.receive(
                RabbitMessagingConfiguration.RECONCILIATION_DLQ,
                5_000
        );
        assertThat(deadLetter).isNotNull();
        Object retryAttempt = deadLetter.getMessageProperties().getHeader(
                ReliableConsumerForwarder.RETRY_ATTEMPT_HEADER
        );
        Object failureCode = deadLetter.getMessageProperties().getHeader(
                ReliableConsumerForwarder.FAILURE_CODE_HEADER
        );
        assertThat(retryAttempt).isEqualTo(2);
        assertThat(failureCode).isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void shouldDeadLetterMissingTaskWithoutDatabaseSideEffects() {
        publish(requestedMessage(9_999_999_999L, "outbox-9204"));

        Message deadLetter = rabbitTemplate.receive(
                RabbitMessagingConfiguration.RECONCILIATION_DLQ,
                5_000
        );
        assertThat(deadLetter).isNotNull();
        Object failureCode = deadLetter.getMessageProperties().getHeader(
                ReliableConsumerForwarder.FAILURE_CODE_HEADER
        );
        assertThat(failureCode).isEqualTo("TASK_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_jobs WHERE id = ?",
                Integer.class,
                9_999_999_999L
        )).isZero();
    }

    private ReconciliationJobResponse createAcceptedJob() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        String externalNo = "RABBIT-" + importJobId;
        fixture.insertTransaction(
                accountId,
                importJobId,
                externalNo,
                TransactionDirection.INCOME,
                "98.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        fixture.insertTransaction(
                accountId,
                null,
                externalNo,
                TransactionDirection.INCOME,
                "98.00",
                BASE_TIME,
                TransactionSource.MANUAL
        );
        return reconciliationJobService.create(importJobId, ownerId);
    }

    private JobRequestedMessage requestedMessage(
            Long reconciliationJobId,
            String messageId) {
        return new JobRequestedMessage(
                messageId,
                OutboxEventType.RECONCILIATION_REQUESTED,
                reconciliationJobId,
                1,
                OffsetDateTime.of(
                        2026, 8, 1, 10, 0, 0, 0,
                        ZoneOffset.ofHours(8)
                )
        );
    }

    private void publish(JobRequestedMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_REQUESTED_ROUTING_KEY,
                message
        );
    }

    private ReconciliationJobResponse waitForTerminal(
            Long reconciliationJobId,
            long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            ReconciliationJobResponse response =
                    reconciliationJobService.getById(reconciliationJobId);
            if (response.status() == ReconciliationJobStatus.COMPLETED
                    || response.status() == ReconciliationJobStatus.FAILED) {
                return response;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(
                "Reconciliation job did not reach a terminal state"
        );
    }

    private void waitForMainQueueToDrain() throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (queueMessageCount(
                    RabbitMessagingConfiguration.RECONCILIATION_QUEUE
            ) == 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Reconciliation queue did not drain");
    }

    private int queueMessageCount(String queue) {
        Object count = rabbitAdmin.getQueueProperties(queue).get(
                RabbitAdmin.QUEUE_MESSAGE_COUNT
        );
        return ((Number) count).intValue();
    }

    private int resultCount(Long reconciliationJobId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_results "
                        + "WHERE reconciliation_job_id = ?",
                Integer.class,
                reconciliationJobId
        );
    }

    private void purgeQueues() {
        for (String queue : new String[]{
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_ONE_QUEUE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_TWO_QUEUE,
                RabbitMessagingConfiguration.RECONCILIATION_DLQ
        }) {
            rabbitAdmin.purgeQueue(queue, false);
        }
    }
}
