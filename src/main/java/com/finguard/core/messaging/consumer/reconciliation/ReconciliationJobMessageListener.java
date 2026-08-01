package com.finguard.core.messaging.consumer.reconciliation;

import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import com.finguard.core.messaging.consumer.config.ReconciliationConsumerConfiguration;
import com.finguard.core.messaging.consumer.exception.InvalidJobRequestedMessageException;
import com.finguard.core.messaging.consumer.failure.ConsumerFailureCode;
import com.finguard.core.messaging.consumer.failure.ConsumerFlow;
import com.finguard.core.messaging.consumer.failure.ConsumerForwardResult;
import com.finguard.core.messaging.consumer.failure.ConsumerRetryPolicy;
import com.finguard.core.messaging.consumer.failure.ConsumerRoute;
import com.finguard.core.messaging.consumer.failure.InvalidRetryAttemptException;
import com.finguard.core.messaging.consumer.failure.ReliableConsumerForwarder;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.reconciliation.exception.InvalidReconciliationOperationException;
import com.finguard.core.reconciliation.exception.ReconciliationJobNotFoundException;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ReconciliationJobMessageListener {

    private final ReconciliationJobMessageHandler handler;
    private final ConsumerRetryPolicy retryPolicy;
    private final ReliableConsumerForwarder forwarder;

    public ReconciliationJobMessageListener(
            ReconciliationJobMessageHandler handler,
            ConsumerRetryPolicy retryPolicy,
            ReliableConsumerForwarder forwarder) {
        this.handler = handler;
        this.retryPolicy = retryPolicy;
        this.forwarder = forwarder;
    }

    @RabbitListener(
            queues = RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
            containerFactory = ReconciliationConsumerConfiguration
                    .CONTAINER_FACTORY,
            autoStartup =
                    "${finguard.messaging.reconciliation-consumer.enabled:true}"
    )
    public void onMessage(
            JobRequestedMessage message,
            Message amqpMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        int attempt;
        try {
            attempt = retryPolicy.parseAttempt(
                    amqpMessage.getMessageProperties().getHeader(
                            ReliableConsumerForwarder.RETRY_ATTEMPT_HEADER
                    )
            );
        } catch (InvalidRetryAttemptException exception) {
            forwardOrRequeue(
                    message,
                    retryPolicy.deadLetter(
                            ConsumerFlow.RECONCILIATION,
                            0
                    ),
                    ConsumerFailureCode.INVALID_RETRY_ATTEMPT,
                    channel,
                    deliveryTag
            );
            return;
        }

        try {
            handler.handle(message);
            channel.basicAck(deliveryTag, false);
        } catch (InvalidJobRequestedMessageException exception) {
            forwardOrRequeue(
                    message,
                    retryPolicy.deadLetter(
                            ConsumerFlow.RECONCILIATION,
                            attempt
                    ),
                    ConsumerFailureCode.INVALID_MESSAGE,
                    channel,
                    deliveryTag
            );
        } catch (ReconciliationJobNotFoundException exception) {
            forwardOrRequeue(
                    message,
                    retryPolicy.deadLetter(
                            ConsumerFlow.RECONCILIATION,
                            attempt
                    ),
                    ConsumerFailureCode.TASK_NOT_FOUND,
                    channel,
                    deliveryTag
            );
        } catch (InvalidReconciliationOperationException exception) {
            handler.markBusinessFailed(message.aggregateId());
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            handleTransientFailure(
                    message,
                    attempt,
                    channel,
                    deliveryTag
            );
        }
    }

    private void handleTransientFailure(
            JobRequestedMessage message,
            int attempt,
            Channel channel,
            long deliveryTag) throws IOException {
        ConsumerRoute route = retryPolicy.transientFailure(
                ConsumerFlow.RECONCILIATION,
                attempt
        );
        if (route.deadLetter()) {
            boolean changed = handler.markRetryExhausted(
                    message.aggregateId()
            );
            if (!changed) {
                channel.basicAck(deliveryTag, false);
                return;
            }
        }
        forwardOrRequeue(
                message,
                route,
                route.deadLetter()
                        ? ConsumerFailureCode.RETRY_EXHAUSTED
                        : ConsumerFailureCode.TRANSIENT_FAILURE,
                channel,
                deliveryTag
        );
    }

    private void forwardOrRequeue(
            JobRequestedMessage message,
            ConsumerRoute route,
            ConsumerFailureCode failureCode,
            Channel channel,
            long deliveryTag) throws IOException {
        ConsumerForwardResult result = forwarder.forward(
                message,
                route,
                failureCode
        );
        if (result.successful()) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        channel.basicNack(deliveryTag, false, true);
    }
}
