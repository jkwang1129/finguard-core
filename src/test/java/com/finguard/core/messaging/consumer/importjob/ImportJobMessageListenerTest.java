package com.finguard.core.messaging.consumer.importjob;

import com.finguard.core.importjob.model.ImportProcessingResult;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobMessageListenerTest {

    private final ImportJobMessageHandler handler =
            mock(ImportJobMessageHandler.class);
    private final Channel channel = mock(Channel.class);
    private final ImportJobMessageListener listener =
            new ImportJobMessageListener(handler);

    @Test
    void shouldAcknowledgeOnlyAfterBusinessHandlerReturns()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message))
                .thenReturn(ImportProcessingResult.PROCESSED);

        listener.onMessage(message, channel, 17L);

        InOrder order = inOrder(handler, channel);
        order.verify(handler).handle(message);
        order.verify(channel).basicAck(17L, false);
    }

    @Test
    void shouldNotAcknowledgeWhenBusinessHandlerFails()
            throws Exception {
        JobRequestedMessage message = message();
        when(handler.handle(message))
                .thenThrow(new IllegalStateException("injected failure"));

        assertThatThrownBy(() ->
                listener.onMessage(message, channel, 18L))
                .isInstanceOf(IllegalStateException.class);

        verify(channel, never()).basicAck(18L, false);
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
}
