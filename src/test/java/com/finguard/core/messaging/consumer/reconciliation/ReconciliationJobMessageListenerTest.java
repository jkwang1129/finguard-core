package com.finguard.core.messaging.consumer.reconciliation;

import com.finguard.core.messaging.consumer.exception.InvalidJobRequestedMessageException;
import com.finguard.core.messaging.consumer.failure.ConsumerFailureCode;
import com.finguard.core.messaging.consumer.failure.ConsumerForwardResult;
import com.finguard.core.messaging.consumer.failure.ConsumerRetryPolicy;
import com.finguard.core.messaging.consumer.failure.ConsumerRoute;
import com.finguard.core.messaging.consumer.failure.ReliableConsumerForwarder;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.observability.FinGuardMetrics;
import com.finguard.core.reconciliation.exception.InvalidReconciliationOperationException;
import com.finguard.core.reconciliation.exception.ReconciliationJobNotFoundException;
import com.finguard.core.reconciliation.model.ReconciliationProcessingResult;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationJobMessageListenerTest {

    private final ReconciliationJobMessageHandler handler =
            mock(ReconciliationJobMessageHandler.class);
    private final ReliableConsumerForwarder forwarder =
            mock(ReliableConsumerForwarder.class);
    private final Channel channel = mock(Channel.class);
    private final FinGuardMetrics metrics = mock(FinGuardMetrics.class);
    private final ReconciliationJobMessageListener listener =
            new ReconciliationJobMessageListener(
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
                .thenReturn(ReconciliationProcessingResult.PROCESSED);

        listener.onMessage(message, amqpMessage(null), channel, 31L);

        InOrder order = inOrder(handler, channel);
        order.verify(handler).handle(message);
        order.verify(channel).basicAck(31L, false);
        verify(forwarder, never()).forward(any(), any(), any());
    }

    @Test
    void shouldRouteTransientFailuresAndExhaustion()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message))
                .thenThrow(new IllegalStateException("injected"));
        when(forwarder.forward(any(), any(), any()))
                .thenReturn(ConsumerForwardResult.SENT);

        listener.onMessage(message, amqpMessage(0), channel, 32L);
        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.TRANSIENT_FAILURE)
        );
        verify(channel).basicAck(32L, false);

        when(handler.markRetryExhausted(42L)).thenReturn(true);
        listener.onMessage(message, amqpMessage(2), channel, 33L);
        verify(handler).markRetryExhausted(42L);
        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.RETRY_EXHAUSTED)
        );
        verify(channel).basicAck(33L, false);
    }

    @Test
    void shouldDeadLetterPermanentMessageAndMissingTask()
            throws Exception {
        JobRequestedMessage message = message();
        when(forwarder.forward(any(), any(), any()))
                .thenReturn(ConsumerForwardResult.SENT);
        when(handler.handle(message))
                .thenThrow(new InvalidJobRequestedMessageException("bad"));

        listener.onMessage(message, amqpMessage(null), channel, 34L);
        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.INVALID_MESSAGE)
        );
        verify(channel).basicAck(34L, false);

        doThrow(new ReconciliationJobNotFoundException(42L))
                .when(handler).handle(message);
        listener.onMessage(message, amqpMessage(null), channel, 35L);
        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.TASK_NOT_FOUND)
        );
        verify(channel).basicAck(35L, false);
    }

    @Test
    void shouldMarkDeterministicBusinessFailureWithoutRetry()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message)).thenThrow(
                new InvalidReconciliationOperationException("invalid input")
        );
        when(handler.markBusinessFailed(42L)).thenReturn(true);

        listener.onMessage(message, amqpMessage(null), channel, 36L);

        verify(handler).markBusinessFailed(42L);
        verify(metrics).recordConsumerFailure(
                com.finguard.core.messaging.consumer.failure
                        .ConsumerFlow.RECONCILIATION,
                ConsumerFailureCode.BUSINESS_FAILED
        );
        verify(channel).basicAck(36L, false);
        verify(forwarder, never()).forward(any(), any(), any());
    }

    @Test
    void shouldRequeueOriginalWhenForwardingFails()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message))
                .thenThrow(new IllegalStateException("injected"));
        when(forwarder.forward(any(), any(), any()))
                .thenReturn(ConsumerForwardResult.PUBLISH_TIMEOUT);

        listener.onMessage(message, amqpMessage(0), channel, 37L);

        verify(channel).basicNack(37L, false, true);
        verify(channel, never()).basicAck(37L, false);
    }

    @Test
    void shouldDeadLetterInvalidRetryAttemptWithoutCallingBusiness()
            throws Exception {
        JobRequestedMessage message = message();
        when(forwarder.forward(any(), any(), any()))
                .thenReturn(ConsumerForwardResult.SENT);

        listener.onMessage(message, amqpMessage(-1), channel, 38L);

        verify(handler, never()).handle(any());
        verify(forwarder).forward(
                eq(message),
                any(ConsumerRoute.class),
                eq(ConsumerFailureCode.INVALID_RETRY_ATTEMPT)
        );
        verify(channel).basicAck(38L, false);
    }

    private JobRequestedMessage message() {
        return new JobRequestedMessage(
                "outbox-11",
                OutboxEventType.RECONCILIATION_REQUESTED,
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
