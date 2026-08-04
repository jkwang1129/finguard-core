package com.finguard.core.statistics.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class StatisticsChangePublisher {

    private final ApplicationEventPublisher eventPublisher;

    public StatisticsChangePublisher(
            ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish() {
        eventPublisher.publishEvent(new StatisticsChangedEvent());
    }
}
