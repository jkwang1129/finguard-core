package com.finguard.core.observability;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.messaging.consumer.failure.ConsumerFailureCode;
import com.finguard.core.messaging.consumer.failure.ConsumerFlow;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinGuardMetricsTest {

    private final SimpleMeterRegistry meterRegistry =
            new SimpleMeterRegistry();
    private final FinGuardMetrics metrics =
            new FinGuardMetrics(meterRegistry);

    @Test
    void recordsOnlyStableImportOutcomeLabels() {
        metrics.recordImportCompletion(
                new ImportJobTerminalEvent(ImportJobStatus.SUCCESS)
        );
        metrics.recordImportCompletion(
                new ImportJobTerminalEvent(
                        ImportJobStatus.PARTIAL_SUCCESS
                )
        );
        metrics.recordImportCompletion(
                new ImportJobTerminalEvent(ImportJobStatus.FAILED)
        );
        metrics.recordImportCompletion(
                new ImportJobTerminalEvent(ImportJobStatus.PENDING)
        );
        metrics.recordImportCompletion(
                new ImportJobTerminalEvent(ImportJobStatus.PROCESSING)
        );

        assertThat(importCounter("success").count()).isEqualTo(1.0);
        assertThat(importCounter("partial_success").count())
                .isEqualTo(1.0);
        assertThat(importCounter("failed").count()).isEqualTo(1.0);
        assertThat(meterRegistry.find(
                FinGuardMetrics.IMPORT_COMPLETED
        ).meters()).hasSize(3);
    }

    @Test
    void recordsReconciliationTimerByBoundedOutcome() {
        Timer.Sample completed = metrics.startReconciliationProcessing();
        metrics.recordReconciliationProcessing(
                completed,
                ReconciliationMetricOutcome.COMPLETED
        );
        Timer.Sample failed = metrics.startReconciliationProcessing();
        metrics.recordReconciliationProcessing(
                failed,
                ReconciliationMetricOutcome.FAILED
        );

        assertThat(reconciliationTimer("completed").count())
                .isEqualTo(1);
        assertThat(reconciliationTimer("failed").count()).isEqualTo(1);
    }

    @Test
    void recordsConsumerFailureAttemptWithFiniteLabels() {
        metrics.recordConsumerFailure(
                ConsumerFlow.IMPORT,
                ConsumerFailureCode.TRANSIENT_FAILURE
        );
        metrics.recordConsumerFailure(
                ConsumerFlow.RECONCILIATION,
                ConsumerFailureCode.RETRY_EXHAUSTED
        );

        assertThat(failureCounter(
                "import",
                "transient_failure"
        ).count()).isEqualTo(1.0);
        assertThat(failureCounter(
                "reconciliation",
                "retry_exhausted"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.find(
                FinGuardMetrics.CONSUMER_FAILURES
        ).meters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .containsExactly("flow", "reason")
        );
    }

    private Counter importCounter(String outcome) {
        return meterRegistry.get(FinGuardMetrics.IMPORT_COMPLETED)
                .tag("outcome", outcome)
                .counter();
    }

    private Timer reconciliationTimer(String outcome) {
        return meterRegistry.get(
                        FinGuardMetrics.RECONCILIATION_PROCESSING
                )
                .tag("outcome", outcome)
                .timer();
    }

    private Counter failureCounter(String flow, String reason) {
        return meterRegistry.get(FinGuardMetrics.CONSUMER_FAILURES)
                .tag("flow", flow)
                .tag("reason", reason)
                .counter();
    }
}
