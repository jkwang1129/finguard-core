package com.finguard.core.messaging.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import java.util.Collection;
import java.util.List;

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
                                        .RECONCILIATION_QUEUE,
                                true,
                                false,
                                false));

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
                                        .RECONCILIATION_QUEUE));
    }
}
