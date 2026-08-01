package com.finguard.core.messaging.outbox;

import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.support.ImportJobTestFixture;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.messaging.outbox.entity.OutboxEvent;
import com.finguard.core.messaging.outbox.mapper.OutboxEventMapper;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.reconciliation.service.ReconciliationJobService;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.reconciliation.vo.ReconciliationJobResponse;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxAcceptanceIntegrationTest {

    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private ReconciliationJobService reconciliationJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private OutboxEventMapper outboxEventMapper;

    private ImportJobTestFixture importFixture;
    private ReconciliationTestFixture reconciliationFixture;

    @BeforeEach
    void setUp() {
        importFixture = new ImportJobTestFixture(jdbcTemplate);
        reconciliationFixture = new ReconciliationTestFixture(
                jdbcTemplate
        );
        reconciliationFixture.clean();
        importFixture.clean();
    }

    @AfterEach
    void tearDown() {
        reset(outboxEventMapper);
        reconciliationFixture.clean();
        importFixture.clean();
    }

    @Test
    void importAcceptanceShouldPersistFileAndOneEventAtomically() {
        Long ownerId = importFixture.insertUser(token());
        byte[] content = csv("ASYNC-IMPORT");

        ImportJobResponse accepted = importJobService.upload(
                "async.csv",
                content,
                ownerId
        );
        ImportJobResponse duplicate = importJobService.upload(
                "renamed.csv",
                content,
                ownerId
        );

        assertThat(accepted.status()).isEqualTo(ImportJobStatus.PENDING);
        assertThat(accepted.startedAt()).isNull();
        assertThat(accepted.finishedAt()).isNull();
        assertThat(duplicate.id()).isEqualTo(accepted.id());
        assertThat(duplicate.duplicateFile()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM import_job_files WHERE import_job_id = ?",
                byte[].class,
                accepted.id()
        )).isEqualTo(content);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE event_type = 'IMPORT_REQUESTED'
                  AND aggregate_id = ?
                  AND status = 'NEW'
                  AND attempts = 0
                """,
                Integer.class,
                accepted.id()
        )).isEqualTo(1);
    }

    @Test
    void outboxInsertFailureShouldRollbackImportJobAndFile() {
        Long ownerId = importFixture.insertUser(token());
        doThrow(new DataAccessResourceFailureException(
                "injected outbox insert failure"
        )).when(outboxEventMapper).insert(any(OutboxEvent.class));

        assertThatThrownBy(() -> importJobService.upload(
                "rollback-outbox.csv",
                csv("ROLLBACK"),
                ownerId
        )).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM import_jobs
                WHERE original_file_name = 'rollback-outbox.csv'
                  AND created_by = ?
                """,
                Integer.class,
                ownerId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM import_job_files ijf
                INNER JOIN import_jobs ij ON ij.id = ijf.import_job_id
                WHERE ij.created_by = ?
                """,
                Integer.class,
                ownerId
        )).isZero();
    }

    @Test
    void reconciliationAcceptanceShouldPersistOneEvent() {
        Long ownerId = reconciliationFixture.insertUser();
        Long importJobId = reconciliationFixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        Long accountId = reconciliationFixture.insertAccount();
        reconciliationFixture.insertTransaction(
                accountId,
                importJobId,
                "ASYNC-RECON",
                TransactionDirection.INCOME,
                "1.00",
                LocalDateTime.of(2026, 8, 1, 10, 0),
                TransactionSource.CSV_IMPORT
        );

        ReconciliationJobResponse accepted =
                reconciliationJobService.create(importJobId, ownerId);
        ReconciliationJobResponse duplicate =
                reconciliationJobService.create(importJobId, ownerId);

        assertThat(accepted.status().name()).isEqualTo("PENDING");
        assertThat(duplicate.id()).isEqualTo(accepted.id());
        assertThat(duplicate.duplicateRequest()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE event_type = ?
                  AND aggregate_id = ?
                  AND status = 'NEW'
                """,
                Integer.class,
                OutboxEventType.RECONCILIATION_REQUESTED.name(),
                accepted.id()
        )).isEqualTo(1);
    }

    private byte[] csv(String externalNo) {
        return (HEADER + "\n"
                + "UNUSED," + externalNo
                + ",INCOME,1.00,2026-08-01 10:00:00,test\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String token() {
        return UUID.randomUUID().toString();
    }
}
