package com.finguard.core.messaging.consumer.importjob;

import com.finguard.core.importjob.service.impl.ImportJobTransactionService;
import com.finguard.core.messaging.consumer.exception.InvalidJobRequestedMessageException;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ImportJobMessageHandlerTest {

    private final ImportJobTransactionService transactionService =
            mock(ImportJobTransactionService.class);
    private final ImportJobMessageHandler handler =
            new ImportJobMessageHandler(transactionService);

    @Test
    void shouldProcessValidatedImportMessage() {
        JobRequestedMessage message = validMessage();

        handler.handle(message);

        verify(transactionService).processPending(42L);
    }

    @Test
    void shouldRejectInvalidMessagesWithoutCallingBusinessService() {
        OffsetDateTime createdAt = OffsetDateTime.of(
                2026, 8, 1, 10, 0, 0, 0,
                ZoneOffset.ofHours(8)
        );
        List<JobRequestedMessage> invalidMessages = List.of(
                new JobRequestedMessage(
                        "invalid-id",
                        OutboxEventType.IMPORT_REQUESTED,
                        42L,
                        1,
                        createdAt
                ),
                new JobRequestedMessage(
                        "outbox-1",
                        OutboxEventType.RECONCILIATION_REQUESTED,
                        42L,
                        1,
                        createdAt
                ),
                new JobRequestedMessage(
                        "outbox-1",
                        OutboxEventType.IMPORT_REQUESTED,
                        0L,
                        1,
                        createdAt
                ),
                new JobRequestedMessage(
                        "outbox-1",
                        OutboxEventType.IMPORT_REQUESTED,
                        42L,
                        2,
                        createdAt
                ),
                new JobRequestedMessage(
                        "outbox-1",
                        OutboxEventType.IMPORT_REQUESTED,
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
