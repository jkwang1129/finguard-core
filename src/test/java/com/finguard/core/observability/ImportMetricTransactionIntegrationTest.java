package com.finguard.core.observability;

import com.finguard.core.importjob.model.ImportJobStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ImportMetricTransactionIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void recordsOnlyAfterCommitAndSkipsRollback() {
        double before = successCount();
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );

        transaction.executeWithoutResult(status ->
                eventPublisher.publishEvent(
                        new ImportJobTerminalEvent(
                                ImportJobStatus.SUCCESS
                        )
                )
        );
        assertThat(successCount()).isEqualTo(before + 1.0);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new ImportJobTerminalEvent(
                            ImportJobStatus.SUCCESS
                    )
            );
            status.setRollbackOnly();
        });
        assertThat(successCount()).isEqualTo(before + 1.0);
    }

    private double successCount() {
        io.micrometer.core.instrument.Counter counter = meterRegistry
                .find(FinGuardMetrics.IMPORT_COMPLETED)
                .tag("outcome", "success")
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
