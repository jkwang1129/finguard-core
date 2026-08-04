package com.finguard.core.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.redis.RedisKeyNames;
import com.finguard.core.statistics.service.StatisticsOverviewService;
import com.finguard.core.statistics.vo.StatisticsOverviewResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "finguard.messaging.import-consumer.enabled=false",
        "finguard.messaging.reconciliation-consumer.enabled=false",
        "finguard.messaging.outbox.enabled=false"
})
@AutoConfigureMockMvc
class StatisticsOverviewIntegrationTest {

    @Autowired
    private StatisticsOverviewService statisticsOverviewService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    @AfterEach
    void clearCache() {
        redisTemplate.delete(RedisKeyNames.STATISTICS_OVERVIEW);
    }

    @Test
    void missLoadsMysqlWritesJsonAndHitKeepsGeneratedAt()
            throws Exception {
        StatisticsOverviewResponse first =
                statisticsOverviewService.getOverview();
        String cachedJson = redisTemplate.opsForValue().get(
                RedisKeyNames.STATISTICS_OVERVIEW
        );
        Long ttl = redisTemplate.getExpire(
                RedisKeyNames.STATISTICS_OVERVIEW,
                TimeUnit.SECONDS
        );

        assertThat(cachedJson).isNotBlank();
        assertThat(cachedJson).doesNotContain("java.lang");
        assertThat(objectMapper.readValue(
                cachedJson,
                StatisticsOverviewResponse.class
        )).isEqualTo(first);
        assertThat(ttl).isBetween(1L, 60L);
        assertThat(first.importJobs().total()).isEqualTo(count(
                "SELECT COUNT(*) FROM import_jobs"
        ));
        assertThat(first.reconciliationJobs().total()).isEqualTo(count(
                "SELECT COUNT(*) FROM reconciliation_jobs"
        ));
        assertThat(first.riskHits().total()).isEqualTo(count(
                "SELECT COUNT(*) FROM risk_hits"
        ));

        StatisticsOverviewResponse second =
                statisticsOverviewService.getOverview();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void corruptJsonIsDeletedAndRebuiltFromMysql() throws Exception {
        redisTemplate.opsForValue().set(
                RedisKeyNames.STATISTICS_OVERVIEW,
                "not-json"
        );

        StatisticsOverviewResponse response =
                statisticsOverviewService.getOverview();
        String rebuilt = redisTemplate.opsForValue().get(
                RedisKeyNames.STATISTICS_OVERVIEW
        );

        assertThat(response).isNotNull();
        assertThat(rebuilt).isNotEqualTo("not-json");
        assertThat(objectMapper.readValue(
                rebuilt,
                StatisticsOverviewResponse.class
        )).isEqualTo(response);
    }

    @Test
    void endpointEnforcesRoleMatrixAndReturnsStableShape()
            throws Exception {
        mockMvc.perform(get("/api/statistics/overview")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_" + RoleCode.ADMIN.name()
                                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").isString())
                .andExpect(jsonPath("$.importJobs.total").isNumber())
                .andExpect(jsonPath("$.reconciliationJobs.total")
                        .isNumber())
                .andExpect(jsonPath("$.reconciliationResults.matched")
                        .isNumber())
                .andExpect(jsonPath("$.riskHits.total").isNumber())
                .andExpect(jsonPath("$.reviewTasks.pending").isNumber());

        mockMvc.perform(get("/api/statistics/overview")
                        .with(jwt().jwt(token -> token.subject("2"))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_" + RoleCode.REVIEWER.name()
                                ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/statistics/overview")
                        .with(jwt().jwt(token -> token.subject("3"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/statistics/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ));
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }
}
