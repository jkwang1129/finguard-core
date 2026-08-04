package com.finguard.core.statistics.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.redis.RedisKeyNames;
import com.finguard.core.redis.config.RedisFeatureProperties;
import com.finguard.core.statistics.vo.StatisticsOverviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StatisticsOverviewCache {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(StatisticsOverviewCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisFeatureProperties properties;

    public StatisticsOverviewCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RedisFeatureProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<StatisticsOverviewResponse> get() {
        String json;
        try {
            json = redisTemplate.opsForValue().get(
                    RedisKeyNames.STATISTICS_OVERVIEW
            );
        } catch (DataAccessException exception) {
            warnUnavailable("read", exception);
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    json,
                    StatisticsOverviewResponse.class
            ));
        } catch (JsonProcessingException exception) {
            LOGGER.warn(
                    "Redis statistics cache contained invalid JSON; "
                            + "treating it as a miss"
            );
            evict();
            return Optional.empty();
        }
    }

    public void put(StatisticsOverviewResponse response) {
        String json;
        try {
            json = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Statistics response could not be serialized",
                    exception
            );
        }
        try {
            redisTemplate.opsForValue().set(
                    RedisKeyNames.STATISTICS_OVERVIEW,
                    json,
                    properties.getStatistics().getCacheTtl()
            );
        } catch (DataAccessException exception) {
            warnUnavailable("write", exception);
        }
    }

    public void evict() {
        try {
            redisTemplate.delete(RedisKeyNames.STATISTICS_OVERVIEW);
        } catch (DataAccessException exception) {
            warnUnavailable("delete", exception);
        }
    }

    private void warnUnavailable(
            String operation,
            DataAccessException exception) {
        LOGGER.warn(
                "Redis statistics cache {} failed; using MySQL truth",
                operation,
                exception
        );
    }
}
