package com.finguard.core.messaging;

import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.rabbitmq.listener.direct.auto-startup=false"
        })
class RabbitMessagingTopologyIntegrationTest {

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void clearQueuesBeforeTest() {
        purgeBusinessQueues();
    }

    @AfterEach
    void purgeDay2Queues() {
        purgeBusinessQueues();
    }

    @Test
    void shouldDeclareBusinessRetryAndDeadLetterQueues() {
        for (String queue : allQueues()) {
            assertThat(rabbitAdmin.getQueueProperties(queue))
                    .as(queue)
                    .isNotNull();
        }
    }

    @Test
    void shouldRouteRequestedMessagesToTheMatchingBusinessQueues() {
        String importMarker = "day2-import-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY,
                importMarker);

        String reconciliationMarker =
                "day2-reconciliation-" + UUID.randomUUID();
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.IMPORT_QUEUE, 5_000))
                .isEqualTo(importMarker);
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE, 250))
                .isNull();

        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                RabbitMessagingConfiguration.RECONCILIATION_REQUESTED_ROUTING_KEY,
                reconciliationMarker);

        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE, 5_000))
                .isEqualTo(reconciliationMarker);
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.IMPORT_QUEUE, 250))
                .isNull();
    }

    @Test
    void shouldNotRouteMessagesWithUnknownRoutingKeys() {
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                "import.unknown",
                "must-not-route");
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                "reconciliation.unknown",
                "must-not-route");

        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.IMPORT_QUEUE, 250))
                .isNull();
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE, 250))
                .isNull();
    }

    @Test
    void shouldDelayLevelOneRetriesAndReturnThemToMainQueues() {
        String importMarker = "day6-import-retry-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                RabbitMessagingConfiguration
                        .IMPORT_RETRY_LEVEL_ONE_ROUTING_KEY,
                importMarker
        );
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.IMPORT_QUEUE,
                1_000)).isNull();
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.IMPORT_QUEUE,
                6_000)).isEqualTo(importMarker);

        String reconciliationMarker =
                "day6-reconciliation-retry-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_ONE_ROUTING_KEY,
                reconciliationMarker
        );
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
                1_000)).isNull();
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
                6_000)).isEqualTo(reconciliationMarker);
    }

    @Test
    void shouldRouteDeadLettersToTheMatchingDlq() {
        String importMarker = "day6-import-dlq-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.DEAD_LETTER_EXCHANGE,
                RabbitMessagingConfiguration.IMPORT_REQUESTED_ROUTING_KEY,
                importMarker
        );
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.IMPORT_DLQ,
                5_000)).isEqualTo(importMarker);
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.RECONCILIATION_DLQ,
                250)).isNull();

        String reconciliationMarker =
                "day6-reconciliation-dlq-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.DEAD_LETTER_EXCHANGE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_REQUESTED_ROUTING_KEY,
                reconciliationMarker
        );
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.RECONCILIATION_DLQ,
                5_000)).isEqualTo(reconciliationMarker);
        assertThat(rabbitTemplate.receiveAndConvert(
                RabbitMessagingConfiguration.IMPORT_DLQ,
                250)).isNull();
    }

    private void purgeBusinessQueues() {
        for (String queue : allQueues()) {
            rabbitAdmin.purgeQueue(queue, false);
        }
    }

    private String[] allQueues() {
        return new String[]{
                RabbitMessagingConfiguration.IMPORT_QUEUE,
                RabbitMessagingConfiguration.IMPORT_RETRY_LEVEL_ONE_QUEUE,
                RabbitMessagingConfiguration.IMPORT_RETRY_LEVEL_TWO_QUEUE,
                RabbitMessagingConfiguration.IMPORT_DLQ,
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_ONE_QUEUE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_TWO_QUEUE,
                RabbitMessagingConfiguration.RECONCILIATION_DLQ
        };
    }
}
