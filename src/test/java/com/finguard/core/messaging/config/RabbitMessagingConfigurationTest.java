package com.finguard.core.messaging.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RabbitMessagingConfigurationTest {

    @Test
    void shouldDeclareExactDurableNonAutoDeleteTopology() {
        Collection<Declarable> declarables =
                new RabbitMessagingConfiguration()
                        .finguardRabbitDeclarables()
                        .getDeclarables();

        List<DirectExchange> exchanges = declarables.stream()
                .filter(DirectExchange.class::isInstance)
                .map(DirectExchange.class::cast)
                .toList();
        assertThat(exchanges)
                .extracting(
                        DirectExchange::getName,
                        DirectExchange::isDurable,
                        DirectExchange::isAutoDelete,
                        DirectExchange::isInternal)
                .containsExactlyInAnyOrder(
                        tuple(
                                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_EXCHANGE,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration
                                        .DEAD_LETTER_EXCHANGE,
                                true,
                                false,
                                false));

        List<Queue> queues = declarables.stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .toList();
        assertThat(queues)
                .extracting(
                        Queue::getName,
                        Queue::isDurable,
                        Queue::isExclusive,
                        Queue::isAutoDelete)
                .containsExactlyInAnyOrder(
                        tuple(
                                RabbitMessagingConfiguration.IMPORT_QUEUE,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration
                                        .IMPORT_RETRY_LEVEL_ONE_QUEUE,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration
                                        .IMPORT_RETRY_LEVEL_TWO_QUEUE,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration.IMPORT_DLQ,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_QUEUE,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_RETRY_LEVEL_ONE_QUEUE,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_RETRY_LEVEL_TWO_QUEUE,
                                true,
                                false,
                                false),
                        tuple(
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_DLQ,
                                true,
                                false,
                                false));

        assertQueueArguments(
                queues,
                RabbitMessagingConfiguration.IMPORT_QUEUE,
                Map.of(
                        "x-dead-letter-exchange",
                        RabbitMessagingConfiguration.DEAD_LETTER_EXCHANGE
                )
        );
        assertQueueArguments(
                queues,
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
                Map.of(
                        "x-dead-letter-exchange",
                        RabbitMessagingConfiguration.DEAD_LETTER_EXCHANGE
                )
        );
        assertRetryQueueArguments(
                queues,
                RabbitMessagingConfiguration.IMPORT_RETRY_LEVEL_ONE_QUEUE,
                RabbitMessagingConfiguration.RETRY_LEVEL_ONE_DELAY_MS,
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY
        );
        assertRetryQueueArguments(
                queues,
                RabbitMessagingConfiguration.IMPORT_RETRY_LEVEL_TWO_QUEUE,
                RabbitMessagingConfiguration.RETRY_LEVEL_TWO_DELAY_MS,
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY
        );
        assertRetryQueueArguments(
                queues,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_ONE_QUEUE,
                RabbitMessagingConfiguration.RETRY_LEVEL_ONE_DELAY_MS,
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_REQUESTED_ROUTING_KEY
        );
        assertRetryQueueArguments(
                queues,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_TWO_QUEUE,
                RabbitMessagingConfiguration.RETRY_LEVEL_TWO_DELAY_MS,
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_REQUESTED_ROUTING_KEY
        );

        List<Binding> bindings = declarables.stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .toList();
        assertThat(bindings)
                .extracting(
                        Binding::getExchange,
                        Binding::getRoutingKey,
                        Binding::getDestination)
                .containsExactlyInAnyOrder(
                        tuple(
                                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                                RabbitMessagingConfiguration
                                        .IMPORT_REQUESTED_ROUTING_KEY,
                                RabbitMessagingConfiguration.IMPORT_QUEUE),
                        tuple(
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_EXCHANGE,
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_REQUESTED_ROUTING_KEY,
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_QUEUE),
                        tuple(
                                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                                RabbitMessagingConfiguration
                                        .IMPORT_RETRY_LEVEL_ONE_ROUTING_KEY,
                                RabbitMessagingConfiguration
                                        .IMPORT_RETRY_LEVEL_ONE_QUEUE),
                        tuple(
                                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                                RabbitMessagingConfiguration
                                        .IMPORT_RETRY_LEVEL_TWO_ROUTING_KEY,
                                RabbitMessagingConfiguration
                                        .IMPORT_RETRY_LEVEL_TWO_QUEUE),
                        tuple(
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_EXCHANGE,
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_RETRY_LEVEL_ONE_ROUTING_KEY,
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_RETRY_LEVEL_ONE_QUEUE),
                        tuple(
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_EXCHANGE,
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_RETRY_LEVEL_TWO_ROUTING_KEY,
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_RETRY_LEVEL_TWO_QUEUE),
                        tuple(
                                RabbitMessagingConfiguration
                                        .DEAD_LETTER_EXCHANGE,
                                RabbitMessagingConfiguration
                                        .IMPORT_REQUESTED_ROUTING_KEY,
                                RabbitMessagingConfiguration.IMPORT_DLQ),
                        tuple(
                                RabbitMessagingConfiguration
                                        .DEAD_LETTER_EXCHANGE,
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_REQUESTED_ROUTING_KEY,
                                RabbitMessagingConfiguration
                                        .RECONCILIATION_DLQ));
    }

    private void assertRetryQueueArguments(
            List<Queue> queues,
            String queueName,
            int ttlMillis,
            String deadLetterExchange,
            String deadLetterRoutingKey) {
        assertQueueArguments(
                queues,
                queueName,
                Map.of(
                        "x-message-ttl", ttlMillis,
                        "x-dead-letter-exchange", deadLetterExchange,
                        "x-dead-letter-routing-key", deadLetterRoutingKey
                )
        );
    }

    private void assertQueueArguments(
            List<Queue> queues,
            String queueName,
            Map<String, Object> expectedArguments) {
        Queue queue = queues.stream()
                .filter(candidate -> candidate.getName().equals(queueName))
                .findFirst()
                .orElseThrow();
        assertThat(queue.getArguments()).containsAllEntriesOf(
                expectedArguments
        );
    }
}
