package com.finguard.core.messaging.consumer.importjob;

import com.finguard.core.importjob.mapper.ImportRowErrorMapper;
import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.importjob.model.ImportProcessingResult;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.service.impl.ImportJobTransactionService;
import com.finguard.core.importjob.support.ImportJobTestFixture;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.messaging.consumer.failure.ConsumerRetryPolicy;
import com.finguard.core.messaging.consumer.failure.ReliableConsumerForwarder;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
class AsyncImportProcessorIntegrationTest {

    private static final String ACCOUNT_PREFIX = "W4D4_PROC_";
    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private ImportJobTransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ImportRowErrorMapper importRowErrorMapper;

    private ImportJobTestFixture fixture;
    private Long ownerId;
    private String accountNo;

    @BeforeEach
    void setUp() {
        fixture = new ImportJobTestFixture(jdbcTemplate);
        clean();
        ownerId = fixture.insertUser(UUID.randomUUID().toString());
        accountNo = ACCOUNT_PREFIX + token();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    account_no, account_name, account_type,
                    currency, status, deleted
                )
                VALUES (?, 'Week 4 Day 4 processor',
                        'BANK', 'CNY', 'ACTIVE', 0)
                """,
                accountNo
        );
    }

    @AfterEach
    void tearDown() {
        reset(importRowErrorMapper);
        clean();
    }

    @Test
    void shouldRestorePersistedFileAndCompleteSuccessAndPartialResults() {
        ImportJobResponse success = accept(
                "success.csv",
                row(accountNo, "SUCCESS-1", "10.00")
        );
        assertThat(transactionService.processPending(success.id()))
                .isEqualTo(ImportProcessingResult.PROCESSED);

        ImportJobResponse completed = importJobService.getById(success.id());
        assertThat(completed.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(completed.totalRows()).isEqualTo(1);
        assertThat(completed.successRows()).isEqualTo(1);
        assertThat(completed.failedRows()).isZero();
        assertThat(completed.startedAt()).isNotNull();
        assertThat(completed.finishedAt()).isNotNull();
        assertThat(transactionService.processPending(success.id()))
                .isEqualTo(ImportProcessingResult.ALREADY_COMPLETED);

        ImportJobResponse partial = accept(
                "partial.csv",
                row(accountNo, "PARTIAL-OK", "5.00"),
                row("MISSING_ACCOUNT", "PARTIAL-BAD", "5.00")
        );
        assertThat(transactionService.processPending(partial.id()))
                .isEqualTo(ImportProcessingResult.PROCESSED);

        ImportJobResponse partialResult =
                importJobService.getById(partial.id());
        assertThat(partialResult.status())
                .isEqualTo(ImportJobStatus.PARTIAL_SUCCESS);
        assertThat(partialResult.totalRows()).isEqualTo(2);
        assertThat(partialResult.successRows()).isEqualTo(1);
        assertThat(partialResult.failedRows()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_errors WHERE import_job_id = ?",
                Integer.class,
                partial.id()
        )).isEqualTo(1);
        assertThat(transactionService.processPending(partial.id()))
                .isEqualTo(ImportProcessingResult.ALREADY_COMPLETED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_errors WHERE import_job_id = ?",
                Integer.class,
                partial.id()
        )).isEqualTo(1);
    }

    @Test
    void shouldCommitFileLevelFailureAsBusinessTerminalState() {
        ImportJobResponse accepted = importJobService.upload(
                "invalid-header.csv",
                "wrong,header\nvalue,value\n"
                        .getBytes(StandardCharsets.UTF_8),
                ownerId
        );

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ImportProcessingResult.PROCESSED);

        ImportJobResponse result = importJobService.getById(accepted.id());
        assertThat(result.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(result.fileErrorCode())
                .isEqualTo(ImportFileErrorCode.INVALID_HEADER);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ImportProcessingResult.ALREADY_COMPLETED);
    }

    @Test
    void shouldRollbackToPendingWhenUnexpectedPersistenceFailureOccurs() {
        ImportJobResponse accepted = accept(
                "rollback.csv",
                row(accountNo, "ROLLBACK-OK", "10.00"),
                row("MISSING_ACCOUNT", "ROLLBACK-BAD", "5.00")
        );
        doThrow(new DataAccessResourceFailureException(
                "injected row error persistence failure"
        )).when(importRowErrorMapper).insertBatch(any());

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(DataAccessResourceFailureException.class);

        ImportJobResponse rolledBack =
                importJobService.getById(accepted.id());
        assertThat(rolledBack.status()).isEqualTo(ImportJobStatus.PENDING);
        assertThat(rolledBack.startedAt()).isNull();
        assertThat(rolledBack.finishedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_errors WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();

        reset(importRowErrorMapper);
        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ImportProcessingResult.PROCESSED);
        ImportJobResponse recovered = importJobService.getById(accepted.id());
        assertThat(recovered.status())
                .isEqualTo(ImportJobStatus.PARTIAL_SUCCESS);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_errors WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isEqualTo(1);
    }

    @Test
    void shouldRejectMissingPersistedFileWithoutBusinessSideEffects() {
        ImportJobResponse accepted = accept(
                "missing-file.csv",
                row(accountNo, "MISSING-FILE", "10.00")
        );
        jdbcTemplate.update(
                "DELETE FROM import_job_files WHERE import_job_id = ?",
                accepted.id()
        );

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Persisted import file is missing");

        ImportJobResponse unchanged =
                importJobService.getById(accepted.id());
        assertThat(unchanged.status()).isEqualTo(ImportJobStatus.PENDING);
        assertThat(unchanged.startedAt()).isNull();
        assertThat(unchanged.finishedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
    }

    @Test
    void shouldSerializeConcurrentDeliveriesForTheSameJob()
            throws Exception {
        ImportJobResponse accepted = accept(
                "concurrent.csv",
                row(accountNo, "CONCURRENT", "12.34")
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ImportProcessingResult> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return transactionService.processPending(accepted.id());
            });
            Future<ImportProcessingResult> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return transactionService.processPending(accepted.id());
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(
                    ImportProcessingResult.PROCESSED,
                    ImportProcessingResult.ALREADY_COMPLETED
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        ImportJobResponse completed = importJobService.getById(accepted.id());
        assertThat(completed.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(completed.totalRows()).isEqualTo(1);
        assertThat(completed.successRows()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isEqualTo(1);
    }

    @Test
    void shouldRecoverACommittedProcessingState() {
        ImportJobResponse accepted = accept(
                "recover-processing.csv",
                row(accountNo, "RECOVER-PROCESSING", "22.22")
        );
        transactionService.markProcessing(accepted.id());

        assertThat(transactionService.processPending(accepted.id()))
                .isEqualTo(ImportProcessingResult.RECOVERED);

        ImportJobResponse completed = importJobService.getById(accepted.id());
        assertThat(completed.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(completed.startedAt()).isNotNull();
        assertThat(completed.finishedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isEqualTo(1);
    }

    @Test
    void shouldCommitOnceWhenAckFailsAndDeliveryReturns()
            throws Exception {
        ImportJobResponse accepted = accept(
                "ack-loss.csv",
                row(accountNo, "ACK-LOSS", "31.00")
        );
        ImportJobMessageListener listener = new ImportJobMessageListener(
                new ImportJobMessageHandler(transactionService),
                new ConsumerRetryPolicy(),
                mock(ReliableConsumerForwarder.class)
        );
        JobRequestedMessage message = requestedMessage(accepted.id());
        Channel failedChannel = mock(Channel.class);
        doThrow(new IOException("injected ACK failure"))
                .when(failedChannel).basicAck(71L, false);

        assertThatThrownBy(() ->
                listener.onMessage(
                        message,
                        new org.springframework.amqp.core.Message(new byte[0]),
                        failedChannel,
                        71L
                ))
                .isInstanceOf(IOException.class)
                .hasMessage("injected ACK failure");

        ImportJobResponse committed = importJobService.getById(accepted.id());
        assertThat(committed.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isEqualTo(1);

        Channel recoveredChannel = mock(Channel.class);
        listener.onMessage(
                message,
                new org.springframework.amqp.core.Message(new byte[0]),
                recoveredChannel,
                72L
        );

        verify(recoveredChannel).basicAck(72L, false);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_errors WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
    }

    private ImportJobResponse accept(String fileName, String... rows) {
        return importJobService.upload(
                fileName,
                (HEADER + "\n" + String.join("\n", rows) + "\n")
                        .getBytes(StandardCharsets.UTF_8),
                ownerId
        );
    }

    private String row(
            String rowAccountNo,
            String externalNo,
            String amount) {
        return String.join(
                ",",
                rowAccountNo,
                externalNo + "-" + token(),
                "INCOME",
                amount,
                "2026-08-01 10:00:00",
                "async processor test"
        );
    }

    private JobRequestedMessage requestedMessage(Long importJobId) {
        return new JobRequestedMessage(
                "outbox-8105",
                OutboxEventType.IMPORT_REQUESTED,
                importJobId,
                1,
                OffsetDateTime.of(
                        2026, 8, 1, 10, 0, 0, 0,
                        ZoneOffset.ofHours(8)
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
