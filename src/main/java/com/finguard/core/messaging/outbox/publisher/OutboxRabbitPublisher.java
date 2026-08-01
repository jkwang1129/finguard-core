package com.finguard.core.messaging.outbox.publisher;

import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import com.finguard.core.messaging.outbox.entity.OutboxEvent;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxRabbitPublisher {

    static final String PUBLISH_NACK = "PUBLISH_NACK";
    static final String PUBLISH_RETURNED = "PUBLISH_RETURNED";
    static final String PUBLISH_TIMEOUT = "PUBLISH_TIMEOUT";
    static final String PUBLISH_FAILED = "PUBLISH_FAILED";

    private final RabbitTemplate rabbitTemplate;
    private final Clock businessClock;
    private final Duration confirmTimeout;

    public OutboxRabbitPublisher(
            RabbitTemplate rabbitTemplate,
            Clock businessClock,
            @Value("${finguard.messaging.outbox.confirm-timeout:5s}")
            Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.businessClock = businessClock;
        this.confirmTimeout = confirmTimeout;
    }

    public OutboxPublishResult publish(OutboxEvent event) {
        String messageId = "outbox-" + event.getId();
        JobRequestedMessage message = new JobRequestedMessage(
                messageId,
                event.getEventType(),
                event.getAggregateId(),
                event.getSchemaVersion(),
                event.getCreatedAt()
                        .atZone(businessClock.getZone())
                        .toOffsetDateTime()
        );
        CorrelationData correlationData =
                new CorrelationData(messageId);
        try {
            rabbitTemplate.convertAndSend(
                    exchange(event.getEventType()),
                    routingKey(event.getEventType()),
                    message,
                    rabbitMessage -> {
                        rabbitMessage.getMessageProperties()
                                .setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT
                                );
                        rabbitMessage.getMessageProperties()
                                .setMessageId(messageId);
                        return rabbitMessage;
                    },
                    correlationData
            );
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                return OutboxPublishResult.failed(PUBLISH_NACK);
            }
            if (correlationData.getReturned() != null) {
                return OutboxPublishResult.failed(PUBLISH_RETURNED);
            }
            return OutboxPublishResult.sent();
        } catch (TimeoutException exception) {
            return OutboxPublishResult.failed(PUBLISH_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return OutboxPublishResult.failed(PUBLISH_FAILED);
        } catch (RuntimeException exception) {
            return OutboxPublishResult.failed(PUBLISH_FAILED);
        } catch (Exception exception) {
            return OutboxPublishResult.failed(PUBLISH_FAILED);
        }
    }

    private String exchange(OutboxEventType type) {
        return switch (type) {
            case IMPORT_REQUESTED ->
                    RabbitMessagingConfiguration.IMPORT_EXCHANGE;
            case RECONCILIATION_REQUESTED ->
                    RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE;
        };
    }

    private String routingKey(OutboxEventType type) {
        return switch (type) {
            case IMPORT_REQUESTED ->
                    RabbitMessagingConfiguration
                            .IMPORT_REQUESTED_ROUTING_KEY;
            case RECONCILIATION_REQUESTED ->
                    RabbitMessagingConfiguration
                            .RECONCILIATION_REQUESTED_ROUTING_KEY;
        };
    }
}
