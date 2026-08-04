package com.finguard.core.ratelimit;

import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.model.UserStatus;
import com.finguard.core.auth.support.AuthTestFixture;
import com.finguard.core.ratelimit.service.RateLimitKeyFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "finguard.redis.rate-limit.login.limit=2",
        "finguard.redis.rate-limit.login.window=30s",
        "finguard.redis.rate-limit.upload.limit=2",
        "finguard.redis.rate-limit.upload.window=30s",
        "finguard.messaging.import-consumer.enabled=false",
        "finguard.messaging.reconciliation-consumer.enabled=false",
        "finguard.messaging.outbox.enabled=false"
})
@AutoConfigureMockMvc
class RateLimitHttpIntegrationTest {

    private static final String REMOTE_ADDRESS = "127.0.0.1";
    private static final String CORRECT_PASSWORD =
            "Correct Password 123!";

    private final List<String> redisKeys = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RateLimitKeyFactory keyFactory;

    private AuthTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new AuthTestFixture(jdbcTemplate);
        clean();
    }

    @AfterEach
    void clean() {
        if (fixture != null) {
            fixture.clean();
        }
        if (!redisKeys.isEmpty()) {
            redisTemplate.delete(redisKeys);
            redisKeys.clear();
        }
    }

    @Test
    void failedLoginCountsAndThirdAttemptReturnsUniform429()
            throws Exception {
        String username = fixture.username(
                "rate-missing-" + token()
        );
        trackLoginKey(username);

        postLogin(username, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"));
        postLogin(username, "wrong-password")
                .andExpect(status().isUnauthorized());
        postLogin(username, "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code")
                        .value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message")
                        .value("Request rate limit exceeded"))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/login"));

        String key = redisKeys.get(0);
        assertThat(key)
                .doesNotContain(username)
                .doesNotContain("wrong-password");
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("3");
    }

    @Test
    void successfulLoginClearsOnlyItsCurrentWindow()
            throws Exception {
        String username = fixture.username(
                "rate-success-" + token()
        );
        fixture.insertUser(
                username,
                UserStatus.ACTIVE,
                passwordEncoder.encode(CORRECT_PASSWORD)
        );
        String key = trackLoginKey(username);

        postLogin(username, "wrong-password")
                .andExpect(status().isUnauthorized());
        assertThat(redisTemplate.hasKey(key)).isTrue();

        postLogin(username, CORRECT_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    void adminUploadCountsBeforeFileValidationAndRejectsThird()
            throws Exception {
        long userId = 991_001L;
        String key = trackUploadKey(userId);

        mockMvc.perform(multipart("/api/import-jobs")
                        .with(jwtFor(userId, RoleCode.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_IMPORT_FILE"));
        mockMvc.perform(multipart("/api/import-jobs")
                        .with(jwtFor(userId, RoleCode.ADMIN)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/import-jobs")
                        .with(jwtFor(userId, RoleCode.ADMIN)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code")
                        .value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.path")
                        .value("/api/import-jobs"));

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("3");
    }

    @Test
    void anonymousAndReviewerUploadDoNotConsumeAdminQuota()
            throws Exception {
        long userId = 991_002L;
        String key = trackUploadKey(userId);

        mockMvc.perform(multipart("/api/import-jobs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/import-jobs")
                        .with(jwtFor(userId, RoleCode.REVIEWER)))
                .andExpect(status().isForbidden());

        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions postLogin(
            String username,
            String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(REMOTE_ADDRESS);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "%s",
                          "password": "%s"
                        }
                        """.formatted(username, password)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor
    jwtFor(long userId, RoleCode roleCode) {
        return jwt()
                .jwt(token -> token.subject(Long.toString(userId)))
                .authorities(new SimpleGrantedAuthority(
                        "ROLE_" + roleCode.name()
                ));
    }

    private String trackLoginKey(String username) {
        String key = keyFactory.loginKey(REMOTE_ADDRESS, username);
        redisKeys.add(key);
        return key;
    }

    private String trackUploadKey(long userId) {
        String key = keyFactory.uploadKey(userId);
        redisKeys.add(key);
        return key;
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
