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
 * <p>Both business queues have consumers, two delayed retry levels, and an
 * isolated dead-letter queue.</p>
 */
@Configuration(proxyBeanMethods = false)
public class RabbitMessagingConfiguration {

    public static final int RETRY_LEVEL_ONE_DELAY_MS = 5_000;
    public static final int RETRY_LEVEL_TWO_DELAY_MS = 30_000;

    public static final String IMPORT_EXCHANGE = "finguard.import.exchange";
    public static final String IMPORT_QUEUE = "finguard.import.queue";
    public static final String IMPORT_RETRY_LEVEL_ONE_QUEUE =
            "finguard.import.retry.1.queue";
    public static final String IMPORT_RETRY_LEVEL_TWO_QUEUE =
            "finguard.import.retry.2.queue";
    public static final String IMPORT_DLQ = "finguard.import.dlq";
    public static final String IMPORT_REQUESTED_ROUTING_KEY = "import.requested";
    public static final String IMPORT_RETRY_LEVEL_ONE_ROUTING_KEY =
            "import.retry.1";
    public static final String IMPORT_RETRY_LEVEL_TWO_ROUTING_KEY =
            "import.retry.2";

    public static final String RECONCILIATION_EXCHANGE = "finguard.reconciliation.exchange";
    public static final String RECONCILIATION_QUEUE = "finguard.reconciliation.queue";
    public static final String RECONCILIATION_RETRY_LEVEL_ONE_QUEUE =
            "finguard.reconciliation.retry.1.queue";
    public static final String RECONCILIATION_RETRY_LEVEL_TWO_QUEUE =
            "finguard.reconciliation.retry.2.queue";
    public static final String RECONCILIATION_DLQ =
            "finguard.reconciliation.dlq";
    public static final String RECONCILIATION_REQUESTED_ROUTING_KEY = "reconciliation.requested";
    public static final String RECONCILIATION_RETRY_LEVEL_ONE_ROUTING_KEY =
            "reconciliation.retry.1";
    public static final String RECONCILIATION_RETRY_LEVEL_TWO_ROUTING_KEY =
            "reconciliation.retry.2";

    public static final String DEAD_LETTER_EXCHANGE = "finguard.dlx";

    @Bean
    public Declarables finguardRabbitDeclarables() {
        DirectExchange importExchange = ExchangeBuilder
                .directExchange(IMPORT_EXCHANGE)
                .durable(true)
                .build();
        Queue importQueue = QueueBuilder
                .durable(IMPORT_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
        Queue importRetryLevelOneQueue = retryQueue(
                IMPORT_RETRY_LEVEL_ONE_QUEUE,
                RETRY_LEVEL_ONE_DELAY_MS,
                IMPORT_EXCHANGE,
                IMPORT_REQUESTED_ROUTING_KEY
        );
        Queue importRetryLevelTwoQueue = retryQueue(
                IMPORT_RETRY_LEVEL_TWO_QUEUE,
                RETRY_LEVEL_TWO_DELAY_MS,
                IMPORT_EXCHANGE,
                IMPORT_REQUESTED_ROUTING_KEY
        );
        Queue importDeadLetterQueue = QueueBuilder
                .durable(IMPORT_DLQ)
                .build();
        Binding importBinding = BindingBuilder
                .bind(importQueue)
                .to(importExchange)
                .with(IMPORT_REQUESTED_ROUTING_KEY);
        Binding importRetryLevelOneBinding = BindingBuilder
                .bind(importRetryLevelOneQueue)
                .to(importExchange)
                .with(IMPORT_RETRY_LEVEL_ONE_ROUTING_KEY);
        Binding importRetryLevelTwoBinding = BindingBuilder
                .bind(importRetryLevelTwoQueue)
                .to(importExchange)
                .with(IMPORT_RETRY_LEVEL_TWO_ROUTING_KEY);

        DirectExchange reconciliationExchange = ExchangeBuilder
                .directExchange(RECONCILIATION_EXCHANGE)
                .durable(true)
                .build();
        Queue reconciliationQueue = QueueBuilder
                .durable(RECONCILIATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
        Queue reconciliationRetryLevelOneQueue = retryQueue(
                RECONCILIATION_RETRY_LEVEL_ONE_QUEUE,
                RETRY_LEVEL_ONE_DELAY_MS,
                RECONCILIATION_EXCHANGE,
                RECONCILIATION_REQUESTED_ROUTING_KEY
        );
        Queue reconciliationRetryLevelTwoQueue = retryQueue(
                RECONCILIATION_RETRY_LEVEL_TWO_QUEUE,
                RETRY_LEVEL_TWO_DELAY_MS,
                RECONCILIATION_EXCHANGE,
                RECONCILIATION_REQUESTED_ROUTING_KEY
        );
        Queue reconciliationDeadLetterQueue = QueueBuilder
                .durable(RECONCILIATION_DLQ)
                .build();
        Binding reconciliationBinding = BindingBuilder
                .bind(reconciliationQueue)
                .to(reconciliationExchange)
                .with(RECONCILIATION_REQUESTED_ROUTING_KEY);
        Binding reconciliationRetryLevelOneBinding = BindingBuilder
                .bind(reconciliationRetryLevelOneQueue)
                .to(reconciliationExchange)
                .with(RECONCILIATION_RETRY_LEVEL_ONE_ROUTING_KEY);
        Binding reconciliationRetryLevelTwoBinding = BindingBuilder
                .bind(reconciliationRetryLevelTwoQueue)
                .to(reconciliationExchange)
                .with(RECONCILIATION_RETRY_LEVEL_TWO_ROUTING_KEY);

        DirectExchange deadLetterExchange = ExchangeBuilder
                .directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
        Binding importDeadLetterBinding = BindingBuilder
                .bind(importDeadLetterQueue)
                .to(deadLetterExchange)
                .with(IMPORT_REQUESTED_ROUTING_KEY);
        Binding reconciliationDeadLetterBinding = BindingBuilder
                .bind(reconciliationDeadLetterQueue)
                .to(deadLetterExchange)
                .with(RECONCILIATION_REQUESTED_ROUTING_KEY);

        return new Declarables(
                importExchange,
                importQueue,
                importBinding,
                importRetryLevelOneQueue,
                importRetryLevelOneBinding,
                importRetryLevelTwoQueue,
                importRetryLevelTwoBinding,
                reconciliationExchange,
                reconciliationQueue,
                reconciliationBinding,
                reconciliationRetryLevelOneQueue,
                reconciliationRetryLevelOneBinding,
                reconciliationRetryLevelTwoQueue,
                reconciliationRetryLevelTwoBinding,
                deadLetterExchange,
                importDeadLetterQueue,
                importDeadLetterBinding,
                reconciliationDeadLetterQueue,
                reconciliationDeadLetterBinding);
    }

    private Queue retryQueue(
            String name,
            int ttlMillis,
            String deadLetterExchange,
            String deadLetterRoutingKey) {
        return QueueBuilder
                .durable(name)
                .ttl(ttlMillis)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
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
