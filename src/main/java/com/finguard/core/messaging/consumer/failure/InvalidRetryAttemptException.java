package com.finguard.core.messaging.consumer.failure;

public class InvalidRetryAttemptException extends RuntimeException {

    public InvalidRetryAttemptException(Object attempt) {
        super("Retry attempt is invalid: " + attempt);
    }
}
