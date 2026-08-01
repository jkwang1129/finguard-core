package com.finguard.core.messaging.consumer.failure;

public record ConsumerRoute(
        String exchange,
        String routingKey,
        int attempt,
        boolean deadLetter) {
}
