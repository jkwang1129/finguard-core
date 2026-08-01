package com.finguard.core.messaging.consumer.failure;

import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumerRetryPolicyTest {

    private final ConsumerRetryPolicy policy = new ConsumerRetryPolicy();

    @Test
    void shouldParseOnlyBoundedIntegralAttempts() {
        assertThat(policy.parseAttempt(null)).isZero();
        assertThat(policy.parseAttempt(0)).isZero();
        assertThat(policy.parseAttempt(1L)).isEqualTo(1);
        assertThat(policy.parseAttempt((short) 2)).isEqualTo(2);

        for (Object invalid : new Object[]{-1, 3, 1.5, "1"}) {
            assertThatThrownBy(() -> policy.parseAttempt(invalid))
                    .isInstanceOf(InvalidRetryAttemptException.class);
        }
    }

    @Test
    void shouldRouteImportThroughTwoRetriesAndThenDlq() {
        assertThat(policy.transientFailure(ConsumerFlow.IMPORT, 0))
                .isEqualTo(new ConsumerRoute(
                        RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                        RabbitMessagingConfiguration
                                .IMPORT_RETRY_LEVEL_ONE_ROUTING_KEY,
                        1,
                        false
                ));
        assertThat(policy.transientFailure(ConsumerFlow.IMPORT, 1))
                .isEqualTo(new ConsumerRoute(
                        RabbitMessagingConfiguration.IMPORT_EXCHANGE,
                        RabbitMessagingConfiguration
                                .IMPORT_RETRY_LEVEL_TWO_ROUTING_KEY,
                        2,
                        false
                ));
        assertThat(policy.transientFailure(ConsumerFlow.IMPORT, 2))
                .isEqualTo(new ConsumerRoute(
                        RabbitMessagingConfiguration
                                .DEAD_LETTER_EXCHANGE,
                        RabbitMessagingConfiguration
                                .IMPORT_REQUESTED_ROUTING_KEY,
                        2,
                        true
                ));
    }

    @Test
    void shouldRouteReconciliationThroughTwoRetriesAndThenDlq() {
        assertThat(policy.transientFailure(
                ConsumerFlow.RECONCILIATION,
                0
        )).isEqualTo(new ConsumerRoute(
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_ONE_ROUTING_KEY,
                1,
                false
        ));
        assertThat(policy.transientFailure(
                ConsumerFlow.RECONCILIATION,
                1
        )).isEqualTo(new ConsumerRoute(
                RabbitMessagingConfiguration.RECONCILIATION_EXCHANGE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_RETRY_LEVEL_TWO_ROUTING_KEY,
                2,
                false
        ));
        assertThat(policy.deadLetter(
                ConsumerFlow.RECONCILIATION,
                2
        )).isEqualTo(new ConsumerRoute(
                RabbitMessagingConfiguration.DEAD_LETTER_EXCHANGE,
                RabbitMessagingConfiguration
                        .RECONCILIATION_REQUESTED_ROUTING_KEY,
                2,
                true
        ));
    }
}
