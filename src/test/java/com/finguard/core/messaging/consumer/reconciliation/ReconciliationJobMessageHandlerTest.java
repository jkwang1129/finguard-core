package com.finguard.core.messaging.consumer.reconciliation;

import com.finguard.core.messaging.consumer.exception.InvalidJobRequestedMessageException;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.reconciliation.model.ReconciliationProcessingResult;
import com.finguard.core.reconciliation.service.impl.ReconciliationJobTransactionService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReconciliationJobMessageHandlerTest {

    private final ReconciliationJobTransactionService transactionService =
            mock(ReconciliationJobTransactionService.class);
    private final ReconciliationJobMessageHandler handler =
            new ReconciliationJobMessageHandler(transactionService);

    @Test
    void shouldProcessValidatedReconciliationMessage() {
        JobRequestedMessage message = validMessage();
        when(transactionService.processPending(42L))
                .thenReturn(ReconciliationProcessingResult.PROCESSED);

        assertThat(handler.handle(message))
                .isEqualTo(ReconciliationProcessingResult.PROCESSED);
        verify(transactionService).processPending(42L);
    }

    @Test
    void shouldRejectInvalidMessagesWithoutCallingBusinessService() {
        OffsetDateTime createdAt = createdAt();
        List<JobRequestedMessage> invalidMessages = List.of(
                new JobRequestedMessage(
                        "invalid-id",
                        OutboxEventType.RECONCILIATION_REQUESTED,
                        42L,
                        1,
                        createdAt
                ),
                new JobRequestedMessage(
                        "outbox-1",
                        OutboxEventType.IMPORT_REQUESTED,
                        42L,
                        1,
                        createdAt
                ),
                new JobRequestedMessage(
                        "outbox-1",
                        OutboxEventType.RECONCILIATION_REQUESTED,
                        0L,
                        1,
                        createdAt
                ),
                new JobRequestedMessage(
                        "outbox-1",
                        OutboxEventType.RECONCILIATION_REQUESTED,
                        42L,
                        2,
                        createdAt
                ),
                new JobRequestedMessage(
                        "outbox-1",
                        OutboxEventType.RECONCILIATION_REQUESTED,
                        42L,
                        1,
                        null
                )
        );

        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(InvalidJobRequestedMessageException.class);
        for (JobRequestedMessage message : invalidMessages) {
            assertThatThrownBy(() -> handler.handle(message))
                    .isInstanceOf(
                            InvalidJobRequestedMessageException.class
                    );
        }
        verifyNoInteractions(transactionService);
    }

    private JobRequestedMessage validMessage() {
        return new JobRequestedMessage(
                "outbox-7",
                OutboxEventType.RECONCILIATION_REQUESTED,
                42L,
                1,
                createdAt()
        );
    }

    private OffsetDateTime createdAt() {
        return OffsetDateTime.of(
                2026, 8, 1, 10, 0, 0, 0,
                ZoneOffset.ofHours(8)
        );
    }
}
