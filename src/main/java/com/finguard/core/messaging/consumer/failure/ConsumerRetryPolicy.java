package com.finguard.core.messaging.consumer.failure;

import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import org.springframework.stereotype.Component;

@Component
public class ConsumerRetryPolicy {

    public static final int MAX_RETRY_ATTEMPTS = 2;

    public int parseAttempt(Object headerValue) {
        if (headerValue == null) {
            return 0;
        }
        if (!(headerValue instanceof Number number)) {
            throw new InvalidRetryAttemptException(headerValue);
        }
        int attempt = number.intValue();
        if (number.doubleValue() != attempt
                || attempt < 0
                || attempt > MAX_RETRY_ATTEMPTS) {
            throw new InvalidRetryAttemptException(headerValue);
        }
        return attempt;
    }

    public ConsumerRoute transientFailure(
            ConsumerFlow flow,
            int currentAttempt) {
        requireValidAttempt(currentAttempt);
        if (currentAttempt >= MAX_RETRY_ATTEMPTS) {
            return deadLetter(flow, currentAttempt);
        }
        int nextAttempt = currentAttempt + 1;
        return new ConsumerRoute(
                businessExchange(flow),
                retryRoutingKey(flow, nextAttempt),
                nextAttempt,
                false
        );
    }

    public ConsumerRoute deadLetter(
            ConsumerFlow flow,
            int currentAttempt) {
        requireValidAttempt(currentAttempt);
        return new ConsumerRoute(
                RabbitMessagingConfiguration.DEAD_LETTER_EXCHANGE,
                requestedRoutingKey(flow),
                currentAttempt,
                true
        );
    }

    public boolean exhausted(int currentAttempt) {
        requireValidAttempt(currentAttempt);
        return currentAttempt >= MAX_RETRY_ATTEMPTS;
    }

    private void requireValidAttempt(int attempt) {
        if (attempt < 0 || attempt > MAX_RETRY_ATTEMPTS) {
            throw new InvalidRetryAttemptException(attempt);
        }
    }

    private String businessExchange(ConsumerFlow flow) {
        return switch (flow) {
            case IMPORT -> RabbitMessagingConfiguration.IMPORT_EXCHANGE;
            case RECONCILIATION -> RabbitMessagingConfiguration
                    .RECONCILIATION_EXCHANGE;
        };
    }

    private String requestedRoutingKey(ConsumerFlow flow) {
        return switch (flow) {
            case IMPORT -> RabbitMessagingConfiguration
                    .IMPORT_REQUESTED_ROUTING_KEY;
            case RECONCILIATION -> RabbitMessagingConfiguration
                    .RECONCILIATION_REQUESTED_ROUTING_KEY;
        };
    }

    private String retryRoutingKey(
            ConsumerFlow flow,
            int nextAttempt) {
        return switch (flow) {
            case IMPORT -> nextAttempt == 1
                    ? RabbitMessagingConfiguration
                    .IMPORT_RETRY_LEVEL_ONE_ROUTING_KEY
                    : RabbitMessagingConfiguration
                    .IMPORT_RETRY_LEVEL_TWO_ROUTING_KEY;
            case RECONCILIATION -> nextAttempt == 1
                    ? RabbitMessagingConfiguration
                    .RECONCILIATION_RETRY_LEVEL_ONE_ROUTING_KEY
                    : RabbitMessagingConfiguration
                    .RECONCILIATION_RETRY_LEVEL_TWO_ROUTING_KEY;
        };
    }
}
