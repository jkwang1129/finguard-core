package com.finguard.core.messaging;

import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RabbitMessagingTopologyIntegrationTest {

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @AfterEach
    void purgeDay2Queues() {
        rabbitAdmin.purgeQueue(RabbitMessagingConfiguration.IMPORT_QUEUE, true);
        rabbitAdmin.purgeQueue(RabbitMessagingConfiguration.RECONCILIATION_QUEUE, true);
    }

    @Test
    void shouldDeclareImportAndReconciliationQueues() {
        assertThat(rabbitAdmin.getQueueProperties(RabbitMessagingConfiguration.IMPORT_QUEUE))
                .isNotNull();
        assertThat(rabbitAdmin.getQueueProperties(RabbitMessagingConfiguration.RECONCILIATION_QUEUE))
                .isNotNull();
    }

    @Test
    void shouldRouteRequestedMessagesToTheMatchingBusinessQueues() {
        String importMarker = "day2-import-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY,
                importMarker);

        String reconciliationMarker = "day2-reconciliation-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                RabbitMessagingConfiguration.RECONCILIATION_REQUESTED_ROUTING_KEY,
                reconciliationMarker);

        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.IMPORT_QUEUE, 5_000))
                .isEqualTo(importMarker);
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE, 5_000))
                .isEqualTo(reconciliationMarker);
    }
}
