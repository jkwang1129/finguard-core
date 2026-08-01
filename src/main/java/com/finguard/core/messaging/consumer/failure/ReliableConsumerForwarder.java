package com.finguard.core.messaging.consumer.failure;

import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ReliableConsumerForwarder {

    public static final String RETRY_ATTEMPT_HEADER =
            "x-finguard-retry-attempt";
    public static final String FAILURE_CODE_HEADER =
            "x-finguard-failure-code";

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public ReliableConsumerForwarder(
            RabbitTemplate rabbitTemplate,
            @Value("${finguard.messaging.consumer-forward.confirm-timeout:5s}")
            Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    public ConsumerForwardResult forward(
            JobRequestedMessage message,
            ConsumerRoute route,
            ConsumerFailureCode failureCode) {
        String correlationId = correlationId(message);
        CorrelationData correlationData = new CorrelationData(
                correlationId
        );
        try {
            rabbitTemplate.convertAndSend(
                    route.exchange(),
                    route.routingKey(),
                    message,
                    rabbitMessage -> {
                        rabbitMessage.getMessageProperties()
                                .setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT
                                );
                        if (message != null
                                && message.messageId() != null
                                && !message.messageId().isBlank()) {
                            rabbitMessage.getMessageProperties()
                                    .setMessageId(message.messageId());
                        }
                        rabbitMessage.getMessageProperties().setHeader(
                                RETRY_ATTEMPT_HEADER,
                                route.attempt()
                        );
                        rabbitMessage.getMessageProperties().setHeader(
                                FAILURE_CODE_HEADER,
                                failureCode.name()
                        );
                        return rabbitMessage;
                    },
                    correlationData
            );
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(
                            confirmTimeout.toMillis(),
                            TimeUnit.MILLISECONDS
                    );
            if (!confirm.isAck()) {
                return ConsumerForwardResult.PUBLISH_NACK;
            }
            if (correlationData.getReturned() != null) {
                return ConsumerForwardResult.PUBLISH_RETURNED;
            }
            return ConsumerForwardResult.SENT;
        } catch (TimeoutException exception) {
            return ConsumerForwardResult.PUBLISH_TIMEOUT;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ConsumerForwardResult.PUBLISH_FAILED;
        } catch (RuntimeException exception) {
            return ConsumerForwardResult.PUBLISH_FAILED;
        } catch (Exception exception) {
            return ConsumerForwardResult.PUBLISH_FAILED;
        }
    }

    private String correlationId(JobRequestedMessage message) {
        if (message != null
                && message.messageId() != null
                && !message.messageId().isBlank()) {
            return "consumer-forward-" + message.messageId();
        }
        return "consumer-forward-" + UUID.randomUUID();
    }
}
