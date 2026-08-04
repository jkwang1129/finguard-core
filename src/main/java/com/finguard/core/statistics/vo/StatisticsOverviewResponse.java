package com.finguard.core.statistics.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record StatisticsOverviewResponse(
        @JsonFormat(
                without = JsonFormat.Feature
                        .ADJUST_DATES_TO_CONTEXT_TIME_ZONE
        )
        OffsetDateTime generatedAt,
        ImportJobStatistics importJobs,
        ReconciliationJobStatistics reconciliationJobs,
        ReconciliationResultStatistics reconciliationResults,
        RiskHitStatistics riskHits,
        ReviewTaskStatistics reviewTasks) {
}
