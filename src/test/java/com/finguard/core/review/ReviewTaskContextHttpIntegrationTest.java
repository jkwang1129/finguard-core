package com.finguard.core.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewTaskContextHttpIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 23, 10, 15, 30, 123_000_000);
    private static final Instant EXPECTED_INSTANT =
            Instant.parse("2026-08-23T02:15:30.123Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private ReconciliationTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void reviewerGetsMinimalRiskHitContextWithoutDatabaseWrites()
            throws Exception {
        Scenario scenario = createRiskTask();
        DatabaseSnapshot before = snapshot();

        MvcResult result = mockMvc.perform(get(
                        "/api/review-tasks/{reviewTaskId}/context",
                        scenario.taskId()
                ).with(role(RoleCode.REVIEWER, scenario.ownerId())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode context = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        assertThat(fieldNames(context)).containsExactlyInAnyOrder(
                "reviewTask",
                "transaction",
                "reconciliation",
                "riskFacts",
                "accountSummary",
                "capturedAt"
        );

        JsonNode reviewTask = context.path("reviewTask");
        assertThat(fieldNames(reviewTask)).containsExactlyInAnyOrder(
                "id",
                "version",
                "status",
                "ruleCode",
                "reasonCode",
                "createdAt"
        );
        assertThat(reviewTask.path("id").longValue())
                .isEqualTo(scenario.taskId());
        assertThat(reviewTask.path("version").intValue()).isZero();
        assertThat(reviewTask.path("status").textValue())
                .isEqualTo("PENDING");
        assertThat(reviewTask.path("ruleCode").textValue())
                .isEqualTo("LARGE_AMOUNT");
        assertThat(reviewTask.path("reasonCode").textValue())
                .isEqualTo("AMOUNT_AT_OR_ABOVE_THRESHOLD");
        assertThat(Instant.parse(reviewTask.path("createdAt").textValue()))
                .isEqualTo(EXPECTED_INSTANT);

        JsonNode transaction = context.path("transaction");
        assertThat(fieldNames(transaction)).containsExactlyInAnyOrder(
                "id",
                "accountId",
                "amount",
                "currency",
                "occurredAt",
                "sourceType"
        );
        assertThat(transaction.path("id").longValue())
                .isEqualTo(scenario.transactionId());
        assertThat(transaction.path("accountId").longValue())
                .isEqualTo(scenario.accountId());
        assertThat(transaction.path("amount").isNumber()).isTrue();
        assertThat(transaction.path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("120000.10"));
        assertThat(transaction.path("currency").textValue())
                .isEqualTo("CNY");
        assertThat(Instant.parse(transaction.path("occurredAt").textValue()))
                .isEqualTo(EXPECTED_INSTANT);
        assertThat(transaction.path("sourceType").textValue())
                .isEqualTo("CSV_IMPORT");

        JsonNode reconciliation = context.path("reconciliation");
        assertThat(fieldNames(reconciliation)).containsExactlyInAnyOrder(
                "resultType",
                "matchMethod",
                "reasonCode"
        );
        assertThat(reconciliation.path("resultType").textValue())
                .isEqualTo("UNMATCHED");
        assertThat(reconciliation.path("matchMethod").textValue())
                .isEqualTo("NONE");
        assertThat(reconciliation.path("reasonCode").textValue())
                .isEqualTo("NO_CANDIDATE");

        JsonNode riskFacts = context.path("riskFacts");
        assertThat(riskFacts).hasSize(2);
        assertRiskFact(
                riskFacts.get(0),
                "OBSERVED_AMOUNT",
                "120000.10",
                "riskHit.observedAmount"
        );
        assertRiskFact(
                riskFacts.get(1),
                "THRESHOLD_AMOUNT",
                "100000.00",
                "riskHit.thresholdAmount"
        );

        JsonNode accountSummary = context.path("accountSummary");
        assertThat(fieldNames(accountSummary)).containsExactlyInAnyOrder(
                "status",
                "displayName"
        );
        assertThat(accountSummary.path("status").textValue())
                .isEqualTo("ACTIVE");
        assertThat(accountSummary.path("displayName").textValue())
                .isEqualTo("Week 3 Day 6");

        String capturedAt = context.path("capturedAt").textValue();
        assertThat(OffsetDateTime.parse(capturedAt).toInstant())
                .isBeforeOrEqualTo(Instant.now());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void reviewerGetsExceptionContextWithNullRuleAndNoRiskFacts()
            throws Exception {
        Scenario scenario = createExceptionTask();
        DatabaseSnapshot before = snapshot();

        MvcResult result = mockMvc.perform(get(
                        "/api/review-tasks/{reviewTaskId}/context",
                        scenario.taskId()
                ).with(role(RoleCode.REVIEWER, scenario.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewTask.version").value(0))
                .andExpect(jsonPath("$.reviewTask.ruleCode").isEmpty())
                .andExpect(jsonPath("$.reviewTask.reasonCode")
                        .value("NO_CANDIDATE"))
                .andExpect(jsonPath("$.riskFacts").isEmpty())
                .andExpect(jsonPath("$.accountSummary.status")
                        .value("ACTIVE"))
                .andReturn();

        JsonNode amount = objectMapper.readTree(
                result.getResponse().getContentAsString()
        ).path("transaction").path("amount");
        assertThat(amount.isNumber()).isTrue();
        assertThat(amount.decimalValue())
                .isEqualByComparingTo(new BigDecimal("42.30"));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void contextRequiresReviewerAndPreservesStableErrorsWithoutWrites()
            throws Exception {
        Scenario scenario = createExceptionTask();
        DatabaseSnapshot before = snapshot();

        mockMvc.perform(get(
                        "/api/review-tasks/{reviewTaskId}/context",
                        scenario.taskId()
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get(
                        "/api/review-tasks/{reviewTaskId}/context",
                        scenario.taskId()
                ).with(role(RoleCode.ADMIN, scenario.ownerId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get(
                        "/api/review-tasks/{reviewTaskId}/context",
                        Long.MAX_VALUE
                ).with(role(RoleCode.REVIEWER, scenario.ownerId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_TASK_NOT_FOUND"));
        mockMvc.perform(get(
                        "/api/review-tasks/{reviewTaskId}/context",
                        0
                ).with(role(RoleCode.REVIEWER, scenario.ownerId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @Transactional
    void databaseSnapshotDetectsFactUpdatesAndSideEffectInserts() {
        Scenario scenario = createExceptionTask();

        DatabaseSnapshot beforeFactUpdate = snapshot();
        assertThat(jdbcTemplate.update(
                "UPDATE transactions SET amount = 43.30 WHERE id = ?",
                scenario.transactionId()
        )).isOne();
        DatabaseSnapshot afterFactUpdate = snapshot();
        assertThat(jdbcTemplate.update(
                "UPDATE transactions SET amount = 42.30 WHERE id = ?",
                scenario.transactionId()
        )).isOne();

        DatabaseSnapshot beforeAuditInsert = snapshot();
        assertThat(jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    action_code,
                    actor_type,
                    actor_user_id,
                    initiated_by,
                    outcome,
                    review_task_id,
                    summary,
                    created_at
                ) VALUES (
                    'REVIEW_CONFIRMED',
                    'USER',
                    ?,
                    ?,
                    'SUCCESS',
                    ?,
                    'Snapshot sensitivity probe',
                    ?
                )
                """,
                scenario.ownerId(),
                scenario.ownerId(),
                scenario.taskId(),
                BASE_TIME
        )).isOne();
        DatabaseSnapshot afterAuditInsert = snapshot();
        assertThat(jdbcTemplate.update(
                """
                DELETE FROM audit_logs
                WHERE action_code = 'REVIEW_CONFIRMED'
                  AND review_task_id = ?
                """,
                scenario.taskId()
        )).isOne();

        Long reconciliationJobId = jdbcTemplate.queryForObject(
                """
                SELECT reconciliation_job_id
                FROM reconciliation_results
                WHERE id = ?
                """,
                Long.class,
                scenario.resultId()
        );
        DatabaseSnapshot beforeOutboxInsert = snapshot();
        assertThat(jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_type,
                    aggregate_id,
                    schema_version,
                    status,
                    attempts,
                    next_attempt_at,
                    created_at,
                    updated_at
                ) VALUES (
                    'RECONCILIATION_REQUESTED',
                    ?,
                    1,
                    'NEW',
                    0,
                    ?,
                    ?,
                    ?
                )
                """,
                reconciliationJobId,
                BASE_TIME.plusYears(10),
                BASE_TIME,
                BASE_TIME
        )).isOne();
        DatabaseSnapshot afterOutboxInsert = snapshot();
        assertThat(jdbcTemplate.update(
                """
                DELETE FROM outbox_events
                WHERE event_type = 'RECONCILIATION_REQUESTED'
                  AND aggregate_id = ?
                """,
                reconciliationJobId
        )).isOne();

        assertSoftly(softly -> {
            softly.assertThat(afterFactUpdate)
                    .as("a non-version transaction update")
                    .isNotEqualTo(beforeFactUpdate);
            softly.assertThat(afterAuditInsert)
                    .as("an audit log insert")
                    .isNotEqualTo(beforeAuditInsert);
            softly.assertThat(afterOutboxInsert)
                    .as("an outbox event insert")
                    .isNotEqualTo(beforeOutboxInsert);
        });
    }

    private Scenario createRiskTask() {
        Scenario exceptionScenario = createBaseTask(
                "RISK-CONTEXT-",
                "120000.10"
        );
        jdbcTemplate.update(
                """
                INSERT INTO risk_hits (
                    reconciliation_result_id,
                    rule_code,
                    reason_code,
                    observed_amount,
                    threshold_amount,
                    reason_summary,
                    created_at
                ) VALUES (?, 'LARGE_AMOUNT',
                          'AMOUNT_AT_OR_ABOVE_THRESHOLD',
                          120000.10, 100000.00,
                          'Amount reached configured threshold', ?)
                """,
                exceptionScenario.resultId(),
                BASE_TIME
        );
        Long riskHitId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM risk_hits
                WHERE reconciliation_result_id = ?
                  AND rule_code = 'LARGE_AMOUNT'
                """,
                Long.class,
                exceptionScenario.resultId()
        );
        Long taskId = insertReviewTask(
                "RISK_HIT",
                null,
                riskHitId
        );
        return new Scenario(
                exceptionScenario.ownerId(),
                exceptionScenario.accountId(),
                exceptionScenario.transactionId(),
                exceptionScenario.resultId(),
                taskId
        );
    }

    private Scenario createExceptionTask() {
        Scenario base = createBaseTask(
                "EXCEPTION-CONTEXT-",
                "42.30"
        );
        Long taskId = insertReviewTask(
                "RECONCILIATION_EXCEPTION",
                base.resultId(),
                null
        );
        return new Scenario(
                base.ownerId(),
                base.accountId(),
                base.transactionId(),
                base.resultId(),
                taskId
        );
    }

    private Scenario createBaseTask(
            String externalPrefix,
            String amount) {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        Long transactionId = fixture.insertTransaction(
                accountId,
                importJobId,
                externalPrefix + importJobId,
                TransactionDirection.EXPENSE,
                amount,
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        jdbcTemplate.update(
                """
                INSERT INTO reconciliation_jobs (
                    import_job_id,
                    status,
                    total_count,
                    matched_count,
                    unmatched_count,
                    duplicate_count,
                    suspicious_count,
                    created_by,
                    started_at,
                    finished_at
                ) VALUES (?, 'COMPLETED', 1, 0, 1, 0, 0, ?, ?, ?)
                """,
                importJobId,
                ownerId,
                BASE_TIME,
                BASE_TIME.plusSeconds(1)
        );
        Long jobId = jdbcTemplate.queryForObject(
                "SELECT id FROM reconciliation_jobs WHERE import_job_id = ?",
                Long.class,
                importJobId
        );
        jdbcTemplate.update(
                """
                INSERT INTO reconciliation_results (
                    reconciliation_job_id,
                    csv_transaction_id,
                    result_type,
                    match_method,
                    reason_code,
                    created_at
                ) VALUES (?, ?, 'UNMATCHED', 'NONE', 'NO_CANDIDATE', ?)
                """,
                jobId,
                transactionId,
                BASE_TIME
        );
        Long resultId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM reconciliation_results
                WHERE reconciliation_job_id = ?
                  AND csv_transaction_id = ?
                """,
                Long.class,
                jobId,
                transactionId
        );
        return new Scenario(
                ownerId,
                accountId,
                transactionId,
                resultId,
                null
        );
    }

    private Long insertReviewTask(
            String sourceType,
            Long reconciliationResultId,
            Long riskHitId) {
        jdbcTemplate.update(
                """
                INSERT INTO review_tasks (
                    source_type,
                    reconciliation_result_id,
                    risk_hit_id,
                    status,
                    version,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 'PENDING', 0, ?, ?)
                """,
                sourceType,
                reconciliationResultId,
                riskHitId,
                BASE_TIME,
                BASE_TIME
        );
        if (riskHitId != null) {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM review_tasks WHERE risk_hit_id = ?",
                    Long.class,
                    riskHitId
            );
        }
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM review_tasks
                WHERE reconciliation_result_id = ?
                """,
                Long.class,
                reconciliationResultId
        );
    }

    private DatabaseSnapshot snapshot() {
        return new DatabaseSnapshot(
                jdbcTemplate.queryForList(
                        """
                        SELECT id,
                               source_type,
                               reconciliation_result_id,
                               risk_hit_id,
                               status,
                               version,
                               reviewed_by,
                               reviewed_at,
                               decision_note,
                               created_at,
                               updated_at
                        FROM review_tasks
                        ORDER BY id
                        """
                ),
                jdbcTemplate.query(
                        "SELECT id, version FROM review_tasks ORDER BY id",
                        (resultSet, rowNum) -> new ReviewTaskVersion(
                                resultSet.getLong("id"),
                                resultSet.getInt("version")
                        )
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT id,
                               account_id,
                               import_job_id,
                               external_transaction_no,
                               direction,
                               amount,
                               transaction_time,
                               description,
                               source,
                               created_at,
                               updated_at,
                               deleted
                        FROM transactions
                        ORDER BY id
                        """
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT id,
                               account_no,
                               account_name,
                               account_type,
                               currency,
                               status,
                               created_at,
                               updated_at,
                               deleted
                        FROM accounts
                        ORDER BY id
                        """
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT id,
                               reconciliation_job_id,
                               csv_transaction_id,
                               manual_transaction_id,
                               result_type,
                               match_method,
                               reason_code,
                               created_at
                        FROM reconciliation_results
                        ORDER BY id
                        """
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT id,
                               reconciliation_result_id,
                               rule_code,
                               reason_code,
                               observed_amount,
                               threshold_amount,
                               observed_count,
                               threshold_count,
                               window_seconds,
                               reason_summary,
                               created_at
                        FROM risk_hits
                        ORDER BY id
                        """
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT id,
                               action_code,
                               actor_type,
                               actor_user_id,
                               initiated_by,
                               outcome,
                               import_job_id,
                               reconciliation_job_id,
                               review_task_id,
                               summary,
                               created_at
                        FROM audit_logs
                        ORDER BY id
                        """
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT id,
                               event_type,
                               aggregate_id,
                               schema_version,
                               status,
                               attempts,
                               next_attempt_at,
                               last_error_summary,
                               sent_at,
                               created_at,
                               updated_at
                        FROM outbox_events
                        ORDER BY id
                        """
                )
        );
    }

    private RequestPostProcessor role(RoleCode role, Long subject) {
        return jwt()
                .jwt(builder -> builder.subject(subject.toString()))
                .authorities(new SimpleGrantedAuthority(
                        "ROLE_" + role.name()
                ));
    }

    private void assertRiskFact(
            JsonNode fact,
            String code,
            String value,
            String source) {
        assertThat(fieldNames(fact)).containsExactlyInAnyOrder(
                "code",
                "value",
                "source"
        );
        assertThat(fact.path("code").textValue()).isEqualTo(code);
        assertThat(fact.path("value").textValue()).isEqualTo(value);
        assertThat(fact.path("source").textValue()).isEqualTo(source);
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> fields = node.fieldNames();
        fields.forEachRemaining(names::add);
        return names;
    }

    private record Scenario(
            Long ownerId,
            Long accountId,
            Long transactionId,
            Long resultId,
            Long taskId) {
    }

    private record ReviewTaskVersion(long id, int version) {
    }

    private record DatabaseSnapshot(
            List<Map<String, Object>> reviewTasks,
            List<ReviewTaskVersion> reviewTaskVersions,
            List<Map<String, Object>> transactions,
            List<Map<String, Object>> accounts,
            List<Map<String, Object>> reconciliationResults,
            List<Map<String, Object>> riskHits,
            List<Map<String, Object>> auditLogs,
            List<Map<String, Object>> outboxEvents) {
    }
}
