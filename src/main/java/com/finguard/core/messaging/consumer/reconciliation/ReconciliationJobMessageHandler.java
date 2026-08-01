package com.finguard.core.messaging.consumer.reconciliation;

import com.finguard.core.messaging.consumer.exception.InvalidJobRequestedMessageException;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.reconciliation.model.ReconciliationProcessingResult;
import com.finguard.core.reconciliation.service.impl.ReconciliationJobTransactionService;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ReconciliationJobMessageHandler {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Pattern MESSAGE_ID_PATTERN =
            Pattern.compile("outbox-[1-9][0-9]*");

    private final ReconciliationJobTransactionService transactionService;

    public ReconciliationJobMessageHandler(
            ReconciliationJobTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public ReconciliationProcessingResult handle(
            JobRequestedMessage message) {
        validate(message);
        return transactionService.processPending(message.aggregateId());
    }

    public boolean markRetryExhausted(Long reconciliationJobId) {
        return transactionService.markRetryExhausted(
                reconciliationJobId
        );
    }

    public boolean markBusinessFailed(Long reconciliationJobId) {
        return transactionService.markBusinessFailed(
                reconciliationJobId
        );
    }

    private void validate(JobRequestedMessage message) {
        if (message == null) {
            throw invalid("Message must not be null");
        }
        if (message.messageId() == null
                || !MESSAGE_ID_PATTERN.matcher(message.messageId()).matches()) {
            throw invalid("Message id is invalid");
        }
        if (message.eventType()
                != OutboxEventType.RECONCILIATION_REQUESTED) {
            throw invalid(
                    "Event type is not supported by reconciliation consumer"
            );
        }
        if (message.aggregateId() == null || message.aggregateId() <= 0) {
            throw invalid("Aggregate id must be positive");
        }
        if (message.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw invalid("Message schema version is not supported");
        }
        if (message.createdAt() == null) {
            throw invalid("Message creation time is required");
        }
    }

    private InvalidJobRequestedMessageException invalid(String message) {
        return new InvalidJobRequestedMessageException(message);
    }
}
