package com.finguard.core.messaging.consumer.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
public class ImportConsumerConfiguration {

    public static final String CONTAINER_FACTORY =
            "importJobListenerContainerFactory";

    @Bean(name = CONTAINER_FACTORY)
    public SimpleRabbitListenerContainerFactory
            importJobListenerContainerFactory(
                    SimpleRabbitListenerContainerFactoryConfigurer configurer,
                    ConnectionFactory connectionFactory,
                    Jackson2JsonMessageConverter messageConverter,
                    @Value("${finguard.messaging.import-consumer.concurrency:1}")
                    int concurrency,
                    @Value("${finguard.messaging.import-consumer.prefetch:1}")
                    int prefetch) {
        Assert.isTrue(concurrency > 0,
                "Import consumer concurrency must be positive");
        Assert.isTrue(prefetch > 0,
                "Import consumer prefetch must be positive");

        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        factory.setPrefetchCount(prefetch);
        return factory;
    }
}
