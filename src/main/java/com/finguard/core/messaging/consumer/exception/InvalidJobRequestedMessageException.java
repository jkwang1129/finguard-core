package com.finguard.core.messaging.consumer.exception;

public class InvalidJobRequestedMessageException
        extends RuntimeException {

    public InvalidJobRequestedMessageException(String message) {
        super(message);
    }
}
