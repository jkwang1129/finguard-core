package com.finguard.core.messaging.outbox.publisher;

public record OutboxPublishResult(
        boolean successful,
        String failureCode) {

    public static OutboxPublishResult sent() {
        return new OutboxPublishResult(true, null);
    }

    public static OutboxPublishResult failed(String failureCode) {
        return new OutboxPublishResult(false, failureCode);
    }
}
