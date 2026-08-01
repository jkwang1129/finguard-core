package com.finguard.core.messaging.consumer.importjob;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.support.ImportJobTestFixture;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import com.finguard.core.messaging.consumer.failure.ReliableConsumerForwarder;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "finguard.messaging.import-consumer.enabled=true",
                "finguard.messaging.import-consumer.concurrency=2",
                "finguard.messaging.outbox.enabled=false"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AsyncImportConsumerIntegrationTest {

    private static final String ACCOUNT_PREFIX = "W4D4_RABBIT_";
    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ImportJobMessageHandler messageHandler;

    private ImportJobTestFixture fixture;
    private Long ownerId;
    private String accountNo;

    @BeforeEach
    void setUp() {
        fixture = new ImportJobTestFixture(jdbcTemplate);
        purgeImportQueue();
        clean();
        ownerId = fixture.insertUser(UUID.randomUUID().toString());
        accountNo = ACCOUNT_PREFIX + token();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    account_no, account_name, account_type,
                    currency, status, deleted
                )
                VALUES (?, 'Week 4 Day 4 RabbitMQ',
                        'BANK', 'CNY', 'ACTIVE', 0)
                """,
                accountNo
        );
    }

    @AfterEach
    void tearDown() {
        reset(messageHandler);
        purgeImportQueue();
        clean();
    }

    @Test
    void shouldConsumeRealRabbitMessageAndAcknowledgeAfterCommit()
            throws Exception {
        String externalNo = "RABBIT-" + token();
        byte[] content = (HEADER + "\n"
                + accountNo + "," + externalNo
                + ",INCOME,18.88,2026-08-01 10:00:00,"
                + "real RabbitMQ test\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportJobResponse accepted = importJobService.upload(
                "real-rabbit.csv",
                content,
                ownerId
        );

        assertThat(accepted.status()).isEqualTo(ImportJobStatus.PENDING);
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY,
                new JobRequestedMessage(
                        "outbox-9001",
                        OutboxEventType.IMPORT_REQUESTED,
                        accepted.id(),
                        1,
                        OffsetDateTime.of(
                                2026, 8, 1, 10, 0, 0, 0,
                                ZoneOffset.ofHours(8)
                        )
                )
        );

        ImportJobResponse completed = waitForTerminal(accepted.id());
        assertThat(completed.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(completed.totalRows()).isEqualTo(1);
        assertThat(completed.successRows()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE import_job_id = ?
                  AND external_transaction_no = ?
                  AND amount = 18.88
                """,
                Integer.class,
                accepted.id(),
                externalNo
        )).isEqualTo(1);
        waitForQueueToDrain();
    }

    @Test
    void shouldConsumeConcurrentDuplicateRabbitMessagesOnlyOnce()
            throws Exception {
        String externalNo = "DUPLICATE-" + token();
        byte[] content = (HEADER + "\n"
                + accountNo + "," + externalNo
                + ",INCOME,28.88,2026-08-01 10:00:00,"
                + "duplicate RabbitMQ test\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportJobResponse accepted = importJobService.upload(
                "duplicate-rabbit.csv",
                content,
                ownerId
        );
        JobRequestedMessage message = new JobRequestedMessage(
                "outbox-9002",
                OutboxEventType.IMPORT_REQUESTED,
                accepted.id(),
                1,
                OffsetDateTime.of(
                        2026, 8, 1, 10, 0, 0, 0,
                        ZoneOffset.ofHours(8)
                )
        );

        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY,
                message
        );
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY,
                message
        );

        ImportJobResponse completed = waitForTerminal(accepted.id());
        assertThat(completed.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(completed.totalRows()).isEqualTo(1);
        assertThat(completed.successRows()).isEqualTo(1);
        waitForQueueToDrain();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE import_job_id = ?
                  AND external_transaction_no = ?
                """,
                Integer.class,
                accepted.id(),
                externalNo
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_errors WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
    }

    @Test
    void shouldDelayOneTransientFailureThenRecover()
            throws Exception {
        ImportJobResponse accepted = acceptOneRow("RETRY-RECOVER");
        doThrow(new IllegalStateException("injected transient failure"))
                .doCallRealMethod()
                .when(messageHandler).handle(any());

        long started = System.nanoTime();
        publish(accepted.id(), "outbox-9101");

        ImportJobResponse completed = waitForTerminal(
                accepted.id(),
                15_000
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started
        );
        assertThat(completed.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(4_500);
        assertThat(rabbitAdmin.getQueueProperties(
                RabbitMessagingConfiguration.IMPORT_DLQ
        ).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(0);
    }

    @Test
    void shouldExhaustTwoRetriesThenFailAndDeadLetter()
            throws Exception {
        ImportJobResponse accepted = acceptOneRow("RETRY-EXHAUST");
        doThrow(new IllegalStateException("injected persistent failure"))
                .when(messageHandler).handle(any());

        long started = System.nanoTime();
        publish(accepted.id(), "outbox-9102");

        ImportJobResponse failed = waitForTerminal(
                accepted.id(),
                45_000
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started
        );
        assertThat(failed.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(failed.errorSummary())
                .isEqualTo("Import retry limit reached");
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(34_000);

        Message deadLetter = rabbitTemplate.receive(
                RabbitMessagingConfiguration.IMPORT_DLQ,
                5_000
        );
        assertThat(deadLetter).isNotNull();
        Object attempt = deadLetter.getMessageProperties().getHeader(
                ReliableConsumerForwarder.RETRY_ATTEMPT_HEADER
        );
        Object failureCode = deadLetter.getMessageProperties().getHeader(
                ReliableConsumerForwarder.FAILURE_CODE_HEADER
        );
        assertThat(attempt).isEqualTo(2);
        assertThat(failureCode).isEqualTo("RETRY_EXHAUSTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
    }

    private ImportJobResponse waitForTerminal(Long importJobId)
            throws InterruptedException {
        return waitForTerminal(importJobId, 10_000);
    }

    private ImportJobResponse waitForTerminal(
            Long importJobId,
            long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            ImportJobResponse response = importJobService.getById(importJobId);
            if (response.status() == ImportJobStatus.SUCCESS
                    || response.status() == ImportJobStatus.PARTIAL_SUCCESS
                    || response.status() == ImportJobStatus.FAILED) {
                return response;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import job did not reach a terminal state");
    }

    private void waitForQueueToDrain() throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            Properties properties = rabbitAdmin.getQueueProperties(
                    RabbitMessagingConfiguration.IMPORT_QUEUE
            );
            Object messages = properties == null
                    ? null
                    : properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            if (Integer.valueOf(0).equals(messages)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import queue did not drain");
    }

    private void purgeImportQueue() {
        for (String queue : new String[]{
                RabbitMessagingConfiguration.IMPORT_QUEUE,
                RabbitMessagingConfiguration.IMPORT_RETRY_LEVEL_ONE_QUEUE,
                RabbitMessagingConfiguration.IMPORT_RETRY_LEVEL_TWO_QUEUE,
                RabbitMessagingConfiguration.IMPORT_DLQ
        }) {
            rabbitAdmin.purgeQueue(queue, false);
        }
    }

    private ImportJobResponse acceptOneRow(String marker) {
        String externalNo = marker + "-" + token();
        byte[] content = (HEADER + "\n"
                + accountNo + "," + externalNo
                + ",INCOME,38.88,2026-08-01 10:00:00,retry test\n")
                .getBytes(StandardCharsets.UTF_8);
        return importJobService.upload(marker + ".csv", content, ownerId);
    }

    private void publish(Long importJobId, String messageId) {
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY,
                new JobRequestedMessage(
                        messageId,
                        OutboxEventType.IMPORT_REQUESTED,
                        importJobId,
                        1,
                        OffsetDateTime.of(
                                2026, 8, 1, 10, 0, 0, 0,
                                ZoneOffset.ofHours(8)
                        )
                )
        );
    }

    private String token() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();
    }

    private void clean() {
        fixture.clean();
        jdbcTemplate.update(
                """
                DELETE t
                FROM transactions t
                INNER JOIN accounts a ON a.id = t.account_id
                WHERE a.account_no LIKE ?
                """,
                ACCOUNT_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM accounts WHERE account_no LIKE ?",
                ACCOUNT_PREFIX + "%"
        );
    }
}
