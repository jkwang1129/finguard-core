package com.finguard.core.messaging.outbox.message;

import com.finguard.core.messaging.outbox.model.OutboxEventType;

import java.time.OffsetDateTime;

public record JobRequestedMessage(
        String messageId,
        OutboxEventType eventType,
        Long aggregateId,
        int schemaVersion,
        OffsetDateTime createdAt) {
}
