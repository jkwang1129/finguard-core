package com.finguard.core;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.messaging.consumer.failure.ConsumerFailureCode;
import com.finguard.core.messaging.consumer.failure.ConsumerFlow;
import com.finguard.core.observability.FinGuardMetrics;
import com.finguard.core.observability.ImportJobTerminalEvent;
import com.finguard.core.observability.ReconciliationMetricOutcome;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinGuardMetrics metrics;

    @Test
    void anonymousPrometheusEndpointExposesSafeSystemAndBusinessMetrics()
            throws Exception {
        metrics.recordImportCompletion(
                new ImportJobTerminalEvent(ImportJobStatus.SUCCESS)
        );
        Timer.Sample sample = metrics.startReconciliationProcessing();
        metrics.recordReconciliationProcessing(
                sample,
                ReconciliationMetricOutcome.COMPLETED
        );
        metrics.recordConsumerFailure(
                ConsumerFlow.IMPORT,
                ConsumerFailureCode.TRANSIENT_FAILURE
        );

        String metricsBody = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(metricsBody)
                .contains("jvm_memory_used_bytes")
                .contains("hikaricp_connections")
                .contains("http_server_requests_seconds")
                .contains("finguard_import_jobs_completed_total")
                .contains("outcome=\"success\"")
                .contains("finguard_reconciliation_processing_seconds")
                .contains("finguard_messaging_consumer_failures_total")
                .contains("reason=\"transient_failure\"")
                .doesNotContain("MYSQL_PASSWORD")
                .doesNotContain("JWT_SECRET_BASE64")
                .doesNotContain("secret-base64");
    }

    @Test
    void unexposedActuatorEndpointRemainsUnavailable() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env").with(jwt()))
                .andExpect(status().isNotFound());
    }
}
