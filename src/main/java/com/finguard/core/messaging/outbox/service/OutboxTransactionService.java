package com.finguard.core.messaging.outbox.service;

import com.finguard.core.messaging.outbox.entity.OutboxEvent;
import com.finguard.core.messaging.outbox.mapper.OutboxEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxTransactionService {

    private final OutboxEventMapper outboxEventMapper;
    private final Clock businessClock;

    public OutboxTransactionService(
            OutboxEventMapper outboxEventMapper,
            Clock businessClock) {
        this.outboxEventMapper = outboxEventMapper;
        this.businessClock = businessClock;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public List<OutboxEvent> findDue(int limit) {
        return outboxEventMapper.findDue(
                LocalDateTime.now(businessClock),
                limit
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long eventId) {
        requireSingleUpdate(outboxEventMapper.markSent(
                eventId,
                LocalDateTime.now(businessClock)
        ), eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(
            Long eventId,
            int previousAttempts,
            String errorSummary) {
        LocalDateTime now = LocalDateTime.now(businessClock);
        int failedAttempts = previousAttempts + 1;
        LocalDateTime nextAttemptAt = now.plus(
                switch (failedAttempts) {
                    case 1 -> java.time.Duration.ofSeconds(5);
                    case 2 -> java.time.Duration.ofSeconds(30);
                    default -> java.time.Duration.ofMinutes(5);
                }
        );
        requireSingleUpdate(outboxEventMapper.markRetry(
                eventId,
                nextAttemptAt,
                errorSummary
        ), eventId);
    }

    private void requireSingleUpdate(int updated, Long eventId) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Outbox event state changed concurrently: " + eventId
            );
        }
    }
}
