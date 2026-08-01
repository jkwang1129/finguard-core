package com.finguard.core.messaging.consumer.failure;

public enum ConsumerFailureCode {
    INVALID_MESSAGE,
    INVALID_RETRY_ATTEMPT,
    TASK_NOT_FOUND,
    BUSINESS_FAILED,
    TRANSIENT_FAILURE,
    RETRY_EXHAUSTED
}
