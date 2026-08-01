package com.finguard.core.messaging.consumer.failure;

public enum ConsumerForwardResult {
    SENT(true),
    PUBLISH_NACK(false),
    PUBLISH_RETURNED(false),
    PUBLISH_TIMEOUT(false),
    PUBLISH_FAILED(false);

    private final boolean successful;

    ConsumerForwardResult(boolean successful) {
        this.successful = successful;
    }

    public boolean successful() {
        return successful;
    }
}
