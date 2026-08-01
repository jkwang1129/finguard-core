package com.finguard.core.messaging.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.support.ImportJobTestFixture;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import com.finguard.core.messaging.outbox.publisher.OutboxPublishResult;
import com.finguard.core.messaging.outbox.publisher.OutboxRabbitPublisher;
import com.finguard.core.messaging.outbox.service.OutboxRelay;
import com.finguard.core.messaging.outbox.entity.OutboxEvent;
import com.finguard.core.messaging.outbox.mapper.OutboxEventMapper;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.messaging.outbox.model.OutboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxRelayIntegrationTest {

    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private ImportJobService importJobService;
    @Autowired
    private OutboxRelay outboxRelay;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @MockitoSpyBean
    private OutboxRabbitPublisher publisher;

    private ImportJobTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ImportJobTestFixture(jdbcTemplate);
        fixture.clean();
        rabbitAdmin.purgeQueue(
                RabbitMessagingConfiguration.IMPORT_QUEUE,
                false
        );
        rabbitAdmin.purgeQueue(
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
                false
        );
    }

    @AfterEach
    void tearDown() {
        reset(publisher);
        rabbitAdmin.purgeQueue(
                RabbitMessagingConfiguration.IMPORT_QUEUE,
                false
        );
        rabbitAdmin.purgeQueue(
                RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
                false
        );
        fixture.clean();
    }

    @Test
    void relayShouldPublishPersistentJsonAndMarkEventSent()
            throws Exception {
        ImportJobResponse accepted = acceptImport("PUBLISH");

        assertThat(outboxRelay.relayDueEvents()).isEqualTo(1);

        Message message = rabbitTemplate.receive(
                RabbitMessagingConfiguration.IMPORT_QUEUE,
                2_000
        );
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        JsonNode json = objectMapper.readTree(message.getBody());
        assertThat(json.get("messageId").asText())
                .startsWith("outbox-");
        assertThat(json.get("eventType").asText())
                .isEqualTo("IMPORT_REQUESTED");
        assertThat(json.get("aggregateId").asLong())
                .isEqualTo(accepted.id());
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM outbox_events
                WHERE event_type = 'IMPORT_REQUESTED'
                  AND aggregate_id = ?
                """,
                String.class,
                accepted.id()
        )).isEqualTo("SENT");
    }

    @Test
    void mandatoryPublishShouldExposeUnroutableReturn()
            throws Exception {
        CorrelationData correlation = new CorrelationData(
                "unroutable-" + UUID.randomUUID()
        );
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                "not.bound",
                "unroutable",
                correlation
        );

        CorrelationData.Confirm confirm = correlation.getFuture()
                .get(5, TimeUnit.SECONDS);
        assertThat(confirm.isAck()).isTrue();
        assertThat(correlation.getReturned()).isNotNull();
    }

    @Test
    void relayShouldRouteReconciliationEventToItsOwnQueue()
            throws Exception {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(
                OutboxEventType.RECONCILIATION_REQUESTED
        );
        event.setAggregateId(987_654_321L);
        event.setSchemaVersion(1);
        event.setStatus(OutboxStatus.NEW);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        outboxEventMapper.insert(event);
        try {
            OutboxPublishResult result = publisher.publish(
                    outboxEventMapper.selectById(event.getId())
            );
            assertThat(result.successful()).isTrue();

            Message message = rabbitTemplate.receive(
                    RabbitMessagingConfiguration.RECONCILIATION_QUEUE,
                    2_000
            );
            assertThat(message).isNotNull();
            JsonNode json = objectMapper.readTree(message.getBody());
            assertThat(json.get("eventType").asText())
                    .isEqualTo("RECONCILIATION_REQUESTED");
            assertThat(json.get("aggregateId").asLong())
                    .isEqualTo(987_654_321L);
        } finally {
            outboxEventMapper.deleteById(event.getId());
        }
    }

    @Test
    void publishFailureShouldRecordRetryAndFiveSecondBackoff() {
        ImportJobResponse accepted = acceptImport("RETRY");
        LocalDateTime before = LocalDateTime.now();
        doReturn(OutboxPublishResult.failed("PUBLISH_FAILED"))
                .when(publisher).publish(any());

        assertThat(outboxRelay.relayDueEvents()).isZero();

        RetryState state = jdbcTemplate.queryForObject(
                """
                SELECT status, attempts, next_attempt_at, last_error_summary
                FROM outbox_events
                WHERE event_type = 'IMPORT_REQUESTED'
                  AND aggregate_id = ?
                """,
                (rs, rowNum) -> new RetryState(
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getTimestamp("next_attempt_at")
                                .toLocalDateTime(),
                        rs.getString("last_error_summary")
                ),
                accepted.id()
        );
        assertThat(state.status()).isEqualTo("RETRY");
        assertThat(state.attempts()).isEqualTo(1);
        assertThat(state.lastError()).isEqualTo("PUBLISH_FAILED");
        assertThat(Duration.between(before, state.nextAttemptAt()))
                .isBetween(Duration.ofSeconds(4), Duration.ofSeconds(7));
    }

    private ImportJobResponse acceptImport(String marker) {
        Long ownerId = fixture.insertUser(UUID.randomUUID().toString());
        byte[] bytes = (HEADER + "\nUNUSED," + marker + ",INCOME,"
                + "1.00,2026-08-01 10:00:00,test\n")
                .getBytes(StandardCharsets.UTF_8);
        return importJobService.upload(marker + ".csv", bytes, ownerId);
    }

    private record RetryState(
            String status,
            int attempts,
            LocalDateTime nextAttemptAt,
            String lastError) {
    }
}
