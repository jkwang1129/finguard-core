package com.finguard.core.observability;

import com.finguard.core.messaging.consumer.failure.ConsumerFailureCode;
import com.finguard.core.messaging.consumer.failure.ConsumerFlow;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Locale;

@Component
public class FinGuardMetrics {

    public static final String IMPORT_COMPLETED =
            "finguard.import.jobs.completed";
    public static final String RECONCILIATION_PROCESSING =
            "finguard.reconciliation.processing";
    public static final String CONSUMER_FAILURES =
            "finguard.messaging.consumer.failures";

    private static final Logger LOGGER = LoggerFactory.getLogger(
            FinGuardMetrics.class
    );

    private final MeterRegistry meterRegistry;

    public FinGuardMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordImportCompletion(ImportJobTerminalEvent event) {
        try {
            String outcome = importOutcomeLabel(event);
            if (outcome == null) {
                LOGGER.warn("Non-terminal import metric event was ignored");
                return;
            }
            Counter.builder(IMPORT_COMPLETED)
                    .description(
                            "Committed import job terminal transitions"
                    )
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException exception) {
            LOGGER.warn("Import completion metric could not be recorded");
        }
    }

    public Timer.Sample startReconciliationProcessing() {
        try {
            return Timer.start(meterRegistry);
        } catch (RuntimeException exception) {
            LOGGER.warn("Reconciliation timer could not be started");
            return null;
        }
    }

    public void recordReconciliationProcessing(
            Timer.Sample sample,
            ReconciliationMetricOutcome outcome) {
        if (sample == null) {
            return;
        }
        try {
            sample.stop(Timer.builder(RECONCILIATION_PROCESSING)
                    .description(
                            "Asynchronous reconciliation processing time"
                    )
                    .tag("outcome", label(outcome))
                    .register(meterRegistry));
        } catch (RuntimeException exception) {
            LOGGER.warn("Reconciliation metric could not be recorded");
        }
    }

    public void recordConsumerFailure(
            ConsumerFlow flow,
            ConsumerFailureCode reason) {
        try {
            Counter.builder(CONSUMER_FAILURES)
                    .description(
                            "Classified message consumer failure attempts"
                    )
                    .tag("flow", label(flow))
                    .tag("reason", label(reason))
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException exception) {
            LOGGER.warn("Consumer failure metric could not be recorded");
        }
    }

    private String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private String importOutcomeLabel(ImportJobTerminalEvent event) {
        if (event == null || event.outcome() == null) {
            return null;
        }
        return switch (event.outcome()) {
            case SUCCESS -> "success";
            case PARTIAL_SUCCESS -> "partial_success";
            case FAILED -> "failed";
            case PENDING, PROCESSING -> null;
        };
    }
}
