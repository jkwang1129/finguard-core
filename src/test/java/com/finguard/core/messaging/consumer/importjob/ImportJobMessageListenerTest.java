package com.finguard.core.messaging.consumer.importjob;

import com.finguard.core.importjob.exception.ImportJobNotFoundException;
import com.finguard.core.importjob.model.ImportProcessingResult;
import com.finguard.core.messaging.consumer.exception.InvalidJobRequestedMessageException;
import com.finguard.core.messaging.consumer.failure.ConsumerFailureCode;
import com.finguard.core.messaging.consumer.failure.ConsumerForwardResult;
import com.finguard.core.messaging.consumer.failure.ConsumerRetryPolicy;
import com.finguard.core.messaging.consumer.failure.ConsumerRoute;
import com.finguard.core.messaging.consumer.failure.ReliableConsumerForwarder;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.observability.FinGuardMetrics;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobMessageListenerTest {

    private final ImportJobMessageHandler handler =
            mock(ImportJobMessageHandler.class);
    private final ReliableConsumerForwarder forwarder =
            mock(ReliableConsumerForwarder.class);
    private final Channel channel = mock(Channel.class);
    private final FinGuardMetrics metrics = mock(FinGuardMetrics.class);
    private final ImportJobMessageListener listener =
            new ImportJobMessageListener(
                    handler,
                    new ConsumerRetryPolicy(),
                    forwarder,
                    metrics
            );

    @Test
    void shouldAcknowledgeOnlyAfterBusinessHandlerReturns()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message))
                .thenReturn(ImportProcessingResult.PROCESSED);

        listener.onMessage(message, amqpMessage(null), channel, 17L);

        InOrder order = inOrder(handler, channel);
        order.verify(handler).handle(message);
        order.verify(channel).basicAck(17L, false);
        verify(forwarder, never()).forward(any(), any(), any());
    }

    @Test
    void shouldForwardTransientFailureToNextRetryBeforeAck()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message))
                .thenThrow(new IllegalStateException("injected failure"));
        when(forwarder.forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.TRANSIENT_FAILURE)
        )).thenReturn(ConsumerForwardResult.SENT);

        listener.onMessage(message, amqpMessage(0), channel, 18L);

        verify(forwarder).forward(
                eq(message),
                eq(new ConsumerRetryPolicy().transientFailure(
                        com.finguard.core.messaging.consumer.failure
                                .ConsumerFlow.IMPORT,
                        0
                )),
                eq(ConsumerFailureCode.TRANSIENT_FAILURE)
        );
        verify(metrics).recordConsumerFailure(
                com.finguard.core.messaging.consumer.failure
                        .ConsumerFlow.IMPORT,
                ConsumerFailureCode.TRANSIENT_FAILURE
        );
        InOrder order = inOrder(forwarder, channel);
        order.verify(forwarder).forward(any(), any(), any());
        order.verify(channel).basicAck(18L, false);
    }

    @Test
    void shouldMarkFailedAndDeadLetterAfterRetryExhaustion()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message))
                .thenThrow(new IllegalStateException("persistent failure"));
        when(handler.markRetryExhausted(42L)).thenReturn(true);
        when(forwarder.forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.RETRY_EXHAUSTED)
        )).thenReturn(ConsumerForwardResult.SENT);

        listener.onMessage(message, amqpMessage(2), channel, 19L);

        verify(handler).markRetryExhausted(42L);
        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.RETRY_EXHAUSTED)
        );
        verify(channel).basicAck(19L, false);
    }

    @Test
    void shouldDeadLetterPermanentMessageErrorsWithoutRetry()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message)).thenThrow(
                new InvalidJobRequestedMessageException("invalid")
        );
        when(forwarder.forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.INVALID_MESSAGE)
        )).thenReturn(ConsumerForwardResult.SENT);

        listener.onMessage(message, amqpMessage(null), channel, 20L);

        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.INVALID_MESSAGE)
        );
        verify(channel).basicAck(20L, false);
        verify(handler, never()).markRetryExhausted(any());
    }

    @Test
    void shouldDeadLetterMissingTaskWithoutCreatingOne()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message)).thenThrow(
                new ImportJobNotFoundException(42L)
        );
        when(forwarder.forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.TASK_NOT_FOUND)
        )).thenReturn(ConsumerForwardResult.SENT);

        listener.onMessage(message, amqpMessage(null), channel, 21L);

        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.TASK_NOT_FOUND)
        );
        verify(channel).basicAck(21L, false);
    }

    @Test
    void shouldRequeueOriginalWhenReliableForwardingFails()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message))
                .thenThrow(new IllegalStateException("injected failure"));
        when(forwarder.forward(any(), any(), any()))
                .thenReturn(ConsumerForwardResult.PUBLISH_NACK);

        listener.onMessage(message, amqpMessage(0), channel, 22L);

        verify(channel).basicNack(22L, false, true);
        verify(channel, never()).basicAck(22L, false);
    }

    @Test
    void shouldDeadLetterInvalidRetryAttemptWithoutCallingBusiness()
            throws Exception {
        JobRequestedMessage message = message();
        when(forwarder.forward(any(), any(), any()))
                .thenReturn(ConsumerForwardResult.SENT);

        listener.onMessage(message, amqpMessage("invalid"), channel, 23L);

        verify(handler, never()).handle(any());
        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.INVALID_RETRY_ATTEMPT)
        );
        verify(channel).basicAck(23L, false);
    }

    private JobRequestedMessage message() {
        return new JobRequestedMessage(
                "outbox-9",
                OutboxEventType.IMPORT_REQUESTED,
                42L,
                1,
                OffsetDateTime.of(
                        2026, 8, 1, 10, 0, 0, 0,
                        ZoneOffset.ofHours(8)
                )
        );
    }

    private Message amqpMessage(Object retryAttempt) {
        Message amqpMessage = new Message(new byte[0]);
        if (retryAttempt != null) {
            amqpMessage.getMessageProperties().setHeader(
                    ReliableConsumerForwarder.RETRY_ATTEMPT_HEADER,
                    retryAttempt
            );
        }
        return amqpMessage;
    }
}
