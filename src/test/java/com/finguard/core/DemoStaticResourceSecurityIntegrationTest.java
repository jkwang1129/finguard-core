package com.finguard.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoStaticResourceSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesDemoPageAnonymously() throws Exception {
        mockMvc.perform(get("/demo/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_HTML
                ))
                .andExpect(content().string(containsString("FinGuard Core")))
                .andExpect(result -> assertThat(
                        result.getResponse().getContentAsString(
                                StandardCharsets.UTF_8
                        ),
                        allOf(
                                containsString("ADMIN 登录"),
                                containsString("REVIEWER 登录"),
                                containsString("流程进度"),
                                containsString("<link rel=\"icon\" href=\"data:,\">"),
                                containsString("我已了解演示会写入当前数据库"),
                                containsString("id=\"persistence-ack\""),
                                containsString("id=\"manual-amount\""),
                                containsString("id=\"csv-match-amount\""),
                                containsString("id=\"csv-time-offset\""),
                                containsString("min=\"-4\" max=\"4\" step=\"1\""),
                                containsString("id=\"risk-amount\""),
                                containsString("id=\"create-data-button\" type=\"button\" disabled"),
                                containsString("id=\"upload-csv-button\" type=\"button\" disabled"),
                                containsString("function createDemoData()"),
                                containsString("function buildCsv(runId, values)"),
                                containsString("async function pollUntil("),
                                containsString("terminalStatuses.includes(job.status)"),
                                containsString("new File([rows.join"),
                                containsString("async function uploadCsv()"),
                                containsString("form.append(\"file\", file, file.name)"),
                                containsString("Polling timed out; last server state is still visible"),
                                containsString("AbortController"),
                                containsString("signal: controller.signal"),
                                containsString("const deadline = Date.now() + timeoutMs;"),
                                containsString("if (job.status === \"FAILED\")")
                        )
                ));
    }

    @Test
    void demoPageDoesNotRelaxBusinessApiAuthentication() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void exposesReconciliationReviewAndRunSummaryWorkflow() throws Exception {
        mockMvc.perform(get("/demo/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        result.getResponse().getContentAsString(
                                StandardCharsets.UTF_8
                        ),
                        allOf(
                                containsString("id=\"reconciliation-job-id\""),
                                containsString("id=\"create-reconciliation-button\""),
                                containsString("id=\"reconciliation-results\""),
                                containsString("id=\"review-tasks\""),
                                containsString("id=\"load-review-tasks-button\""),
                                containsString("id=\"load-run-summary-button\""),
                                containsString("id=\"run-audit-logs\""),
                                containsString("id=\"database-statistics\""),
                                containsString("async function createReconciliation()"),
                                containsString("async function loadRunReviewTasks()"),
                                containsString("async function decideReview(task, decision)"),
                                containsString("async function loadRunSummary()"),
                                containsString("/api/reconciliation-jobs"),
                                containsString("/api/review-tasks?status=PENDING&size=100"),
                                containsString("/api/audit-logs?page=1&size=100"),
                                containsString("/api/statistics/overview"),
                                containsString("REVIEW_VERSION_CONFLICT"),
                                containsString("demoState.reconciliationAttempted = true"),
                                containsString("!demoState.persistenceAcknowledged"),
                                containsString("method: \"PATCH\""),
                                containsString("note: \"FinGuard live demo review\""),
                                containsString("result.csvTransactionId"),
                                containsString("entry.reviewTaskId"),
                                containsString("连接数据库的聚合数据")
                        )
                ));
    }

    @Test
    void distinguishesImportPollingTimeoutsFromApiErrors() throws Exception {
        mockMvc.perform(get("/demo/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        result.getResponse().getContentAsString(
                                StandardCharsets.UTF_8
                        ),
                        allOf(
                                containsString("const timedOut = error?.message === \"Polling timed out; last server state is still visible\";"),
                                containsString("demoState.importPollTimedOut = timedOut;"),
                                containsString("apiFailureMessage(error, \"查询导入状态\")")
                        )
                ));
    }
}
