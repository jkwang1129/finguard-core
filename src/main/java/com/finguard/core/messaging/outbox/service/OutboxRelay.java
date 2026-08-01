package com.finguard.core.messaging.outbox.service;

import com.finguard.core.messaging.outbox.entity.OutboxEvent;
import com.finguard.core.messaging.outbox.publisher.OutboxPublishResult;
import com.finguard.core.messaging.outbox.publisher.OutboxRabbitPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxRelay {

    private final OutboxTransactionService transactionService;
    private final OutboxRabbitPublisher publisher;
    private final int batchSize;

    public OutboxRelay(
            OutboxTransactionService transactionService,
            OutboxRabbitPublisher publisher,
            @Value("${finguard.messaging.outbox.batch-size:50}")
            int batchSize) {
        this.transactionService = transactionService;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    public int relayDueEvents() {
        int sent = 0;
        for (OutboxEvent event : transactionService.findDue(batchSize)) {
            OutboxPublishResult result = publisher.publish(event);
            if (result.successful()) {
                transactionService.markSent(event.getId());
                sent++;
            } else {
                transactionService.markRetry(
                        event.getId(),
                        event.getAttempts(),
                        result.failureCode()
                );
            }
        }
        return sent;
    }
}
