package com.finguard.core.messaging.outbox.model;

public enum OutboxStatus {
    NEW,
    RETRY,
    SENT
}
