package com.finguard.core.statistics;

import com.finguard.core.redis.RedisKeyNames;
import com.finguard.core.statistics.event.StatisticsChangePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "finguard.messaging.import-consumer.enabled=false",
        "finguard.messaging.reconciliation-consumer.enabled=false",
        "finguard.messaging.outbox.enabled=false"
})
@Import(StatisticsCacheInvalidationIntegrationTest.Configuration.class)
class StatisticsCacheInvalidationIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TransactionalPublisher transactionalPublisher;

    @AfterEach
    void clearCache() {
        redisTemplate.delete(RedisKeyNames.STATISTICS_OVERVIEW);
    }

    @Test
    void commitEvictsCacheAfterTransactionCompletes() {
        putMarker();

        transactionalPublisher.publishAndCommit();

        assertThat(redisTemplate.hasKey(
                RedisKeyNames.STATISTICS_OVERVIEW
        )).isFalse();
    }

    @Test
    void rollbackDoesNotEvictCache() {
        putMarker();

        assertThatThrownBy(
                transactionalPublisher::publishAndRollback
        ).isInstanceOf(IllegalStateException.class);

        assertThat(redisTemplate.opsForValue().get(
                RedisKeyNames.STATISTICS_OVERVIEW
        )).isEqualTo("marker");
    }

    private void putMarker() {
        redisTemplate.opsForValue().set(
                RedisKeyNames.STATISTICS_OVERVIEW,
                "marker"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {

        @Bean
        TransactionalPublisher transactionalPublisher(
                StatisticsChangePublisher publisher) {
            return new TransactionalPublisher(publisher);
        }
    }

    static class TransactionalPublisher {

        private final StatisticsChangePublisher publisher;

        TransactionalPublisher(StatisticsChangePublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishAndCommit() {
            publisher.publish();
        }

        @Transactional
        public void publishAndRollback() {
            publisher.publish();
            throw new IllegalStateException("rollback");
        }
    }
}
