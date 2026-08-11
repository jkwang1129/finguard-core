package com.finguard.core.messaging.consumer.importjob;

import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import com.finguard.core.messaging.consumer.config.ImportConsumerConfiguration;
import com.finguard.core.messaging.consumer.exception.InvalidJobRequestedMessageException;
import com.finguard.core.messaging.consumer.failure.ConsumerFailureCode;
import com.finguard.core.messaging.consumer.failure.ConsumerFlow;
import com.finguard.core.messaging.consumer.failure.ConsumerForwardResult;
import com.finguard.core.messaging.consumer.failure.ConsumerRetryPolicy;
import com.finguard.core.messaging.consumer.failure.ConsumerRoute;
import com.finguard.core.messaging.consumer.failure.InvalidRetryAttemptException;
import com.finguard.core.messaging.consumer.failure.ReliableConsumerForwarder;
import com.finguard.core.importjob.exception.ImportJobNotFoundException;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.observability.FinGuardMetrics;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ImportJobMessageListener {

    private final ImportJobMessageHandler handler;
    private final ConsumerRetryPolicy retryPolicy;
    private final ReliableConsumerForwarder forwarder;
    private final FinGuardMetrics metrics;

    public ImportJobMessageListener(
            ImportJobMessageHandler handler,
            ConsumerRetryPolicy retryPolicy,
            ReliableConsumerForwarder forwarder,
            FinGuardMetrics metrics) {
        this.handler = handler;
        this.retryPolicy = retryPolicy;
        this.forwarder = forwarder;
        this.metrics = metrics;
    }

    @RabbitListener(
            queues = RabbitMessagingConfiguration.IMPORT_QUEUE,
            containerFactory = ImportConsumerConfiguration.CONTAINER_FACTORY,
            autoStartup =
                    "${finguard.messaging.import-consumer.enabled:true}"
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
                    retryPolicy.deadLetter(ConsumerFlow.IMPORT, 0),
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
                    retryPolicy.deadLetter(ConsumerFlow.IMPORT, attempt),
                    ConsumerFailureCode.INVALID_MESSAGE,
                    channel,
                    deliveryTag
            );
        } catch (ImportJobNotFoundException exception) {
            forwardOrRequeue(
                    message,
                    retryPolicy.deadLetter(ConsumerFlow.IMPORT, attempt),
                    ConsumerFailureCode.TASK_NOT_FOUND,
                    channel,
                    deliveryTag
            );
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
                ConsumerFlow.IMPORT,
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
        metrics.recordConsumerFailure(
                ConsumerFlow.IMPORT,
                failureCode
        );
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
