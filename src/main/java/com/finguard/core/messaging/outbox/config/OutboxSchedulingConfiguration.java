package com.finguard.core.messaging.outbox.config;

import com.finguard.core.messaging.outbox.service.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class OutboxSchedulingConfiguration {

    @Component
    @ConditionalOnProperty(
            prefix = "finguard.messaging.outbox",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    static class OutboxRelayScheduler {

        private final OutboxRelay outboxRelay;

        OutboxRelayScheduler(OutboxRelay outboxRelay) {
            this.outboxRelay = outboxRelay;
        }

        @Scheduled(
                fixedDelayString =
                        "${finguard.messaging.outbox.fixed-delay-ms:1000}"
        )
        void relay() {
            outboxRelay.relayDueEvents();
        }
    }
}
