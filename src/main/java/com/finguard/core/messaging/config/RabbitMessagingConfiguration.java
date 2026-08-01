package com.finguard.core.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;

/**
 * Declares the durable business topology used by the asynchronous flows.
 *
 * <p>The primary import queue has a Day 4 consumer. Reconciliation consumers,
 * retry queues, and dead-letter handling belong to later days.</p>
 */
@Configuration(proxyBeanMethods = false)
public class RabbitMessagingConfiguration {

    public static final String IMPORT_EXCHANGE = "finguard.import.exchange";
    public static final String IMPORT_QUEUE = "finguard.import.queue";
    public static final String IMPORT_REQUESTED_ROUTING_KEY = "import.requested";

    public static final String RECONCILIATION_EXCHANGE = "finguard.reconciliation.exchange";
    public static final String RECONCILIATION_QUEUE = "finguard.reconciliation.queue";
    public static final String RECONCILIATION_REQUESTED_ROUTING_KEY = "reconciliation.requested";

    @Bean
    public Declarables finguardRabbitDeclarables() {
        DirectExchange importExchange = ExchangeBuilder
                .directExchange(IMPORT_EXCHANGE)
                .durable(true)
                .build();
        Queue importQueue = QueueBuilder
                .durable(IMPORT_QUEUE)
                .build();
        Binding importBinding = BindingBuilder
                .bind(importQueue)
                .to(importExchange)
                .with(IMPORT_REQUESTED_ROUTING_KEY);

        DirectExchange reconciliationExchange = ExchangeBuilder
                .directExchange(RECONCILIATION_EXCHANGE)
                .durable(true)
                .build();
        Queue reconciliationQueue = QueueBuilder
                .durable(RECONCILIATION_QUEUE)
                .build();
        Binding reconciliationBinding = BindingBuilder
                .bind(reconciliationQueue)
                .to(reconciliationExchange)
                .with(RECONCILIATION_REQUESTED_ROUTING_KEY);

        return new Declarables(
                importExchange,
                importQueue,
                importBinding,
                reconciliationExchange,
                reconciliationQueue,
                reconciliationBinding);
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter(
            ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplateCustomizer reliableRabbitTemplateCustomizer(
            Jackson2JsonMessageConverter messageConverter) {
        return template -> {
            template.setMessageConverter(messageConverter);
            template.setMandatory(true);
        };
    }
}
