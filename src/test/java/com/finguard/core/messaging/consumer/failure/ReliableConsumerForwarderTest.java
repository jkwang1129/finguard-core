package com.finguard.core.messaging.consumer.failure;

import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ReliableConsumerForwarderTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    @Test
    void shouldWaitForConfirmAndAddOnlySafeRoutingHeaders()
            throws Exception {
        AtomicReference<Message> published = new AtomicReference<>();
        doAnswer(invocation -> {
            assertThat(invocation.getArgument(0, String.class))
                    .isEqualTo("exchange");
            assertThat(invocation.getArgument(1, String.class))
                    .isEqualTo("retry.1");
            MessagePostProcessor processor = invocation.getArgument(3);
            published.set(processor.postProcessMessage(
                    new Message(new byte[0])
            ));
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        ReliableConsumerForwarder forwarder =
                new ReliableConsumerForwarder(
                        rabbitTemplate,
                        Duration.ofSeconds(1)
                );
        ConsumerForwardResult result = forwarder.forward(
                message(),
                new ConsumerRoute("exchange", "retry.1", 1, false),
                ConsumerFailureCode.TRANSIENT_FAILURE
        );

        assertThat(result).isEqualTo(ConsumerForwardResult.SENT);
        assertThat(published.get().getMessageProperties()
                .getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(published.get().getMessageProperties().getMessageId())
                .isEqualTo("outbox-7");
        Object retryAttempt = published.get().getMessageProperties()
                .getHeader(
                        ReliableConsumerForwarder.RETRY_ATTEMPT_HEADER
                );
        Object failureCode = published.get().getMessageProperties()
                .getHeader(
                        ReliableConsumerForwarder.FAILURE_CODE_HEADER
                );
        assertThat(retryAttempt).isEqualTo(1);
        assertThat(failureCode).isEqualTo("TRANSIENT_FAILURE");
    }

    @Test
    void shouldRejectNackAndMandatoryReturn() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(false, "rejected")
            );
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
        ReliableConsumerForwarder forwarder =
                new ReliableConsumerForwarder(
                        rabbitTemplate,
                        Duration.ofSeconds(1)
                );
        assertThat(forwarder.forward(
                message(),
                route(),
                ConsumerFailureCode.RETRY_EXHAUSTED
        )).isEqualTo(ConsumerForwardResult.PUBLISH_NACK);

        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.setReturned(new ReturnedMessage(
                    new Message(new byte[0]),
                    312,
                    "NO_ROUTE",
                    "exchange",
                    "routing"
            ));
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
        assertThat(forwarder.forward(
                message(),
                route(),
                ConsumerFailureCode.RETRY_EXHAUSTED
        )).isEqualTo(ConsumerForwardResult.PUBLISH_RETURNED);
    }

    @Test
    void shouldReportTimeoutAndPublishException() {
        ReliableConsumerForwarder immediateTimeout =
                new ReliableConsumerForwarder(
                        rabbitTemplate,
                        Duration.ZERO
                );
        assertThat(immediateTimeout.forward(
                message(),
                route(),
                ConsumerFailureCode.TRANSIENT_FAILURE
        )).isEqualTo(ConsumerForwardResult.PUBLISH_TIMEOUT);

        doThrow(new IllegalStateException("injected"))
                .when(rabbitTemplate).convertAndSend(
                        anyString(),
                        anyString(),
                        any(),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class)
                );
        ReliableConsumerForwarder failing =
                new ReliableConsumerForwarder(
                        rabbitTemplate,
                        Duration.ofSeconds(1)
                );
        assertThat(failing.forward(
                message(),
                route(),
                ConsumerFailureCode.TRANSIENT_FAILURE
        )).isEqualTo(ConsumerForwardResult.PUBLISH_FAILED);
    }

    private ConsumerRoute route() {
        return new ConsumerRoute("exchange", "routing", 2, true);
    }

    private JobRequestedMessage message() {
        return new JobRequestedMessage(
                "outbox-7",
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
