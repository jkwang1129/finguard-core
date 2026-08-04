package com.finguard.core.statistics.service;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.risk.model.RiskRuleCode;
import com.finguard.core.statistics.mapper.StatisticsMapper;
import com.finguard.core.statistics.model.StatusCountRow;
import com.finguard.core.statistics.vo.ImportJobStatistics;
import com.finguard.core.statistics.vo.ReconciliationJobStatistics;
import com.finguard.core.statistics.vo.ReconciliationResultStatistics;
import com.finguard.core.statistics.vo.ReviewTaskStatistics;
import com.finguard.core.statistics.vo.RiskHitStatistics;
import com.finguard.core.statistics.vo.StatisticsOverviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatisticsAggregationService {

    private final StatisticsMapper statisticsMapper;
    private final Clock businessClock;

    public StatisticsAggregationService(
            StatisticsMapper statisticsMapper,
            Clock businessClock) {
        this.statisticsMapper = statisticsMapper;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true)
    public StatisticsOverviewResponse aggregate() {
        Map<String, Long> importJobs = counts(
                statisticsMapper.countImportJobsByStatus(),
                ImportJobStatus.class
        );
        Map<String, Long> reconciliationJobs = counts(
                statisticsMapper.countReconciliationJobsByStatus(),
                ReconciliationJobStatus.class
        );
        Map<String, Long> reconciliationResults = counts(
                statisticsMapper.countReconciliationResultsByType(),
                ReconciliationResultType.class
        );
        Map<String, Long> riskHits = counts(
                statisticsMapper.countRiskHitsByRule(),
                RiskRuleCode.class
        );
        Map<String, Long> reviewTasks = counts(
                statisticsMapper.countReviewTasksByStatus(),
                ReviewTaskStatus.class
        );

        return new StatisticsOverviewResponse(
                OffsetDateTime.now(businessClock)
                        .truncatedTo(ChronoUnit.MILLIS),
                new ImportJobStatistics(
                        sum(importJobs),
                        value(importJobs, ImportJobStatus.PENDING),
                        value(importJobs, ImportJobStatus.PROCESSING),
                        value(importJobs, ImportJobStatus.SUCCESS),
                        value(importJobs, ImportJobStatus.PARTIAL_SUCCESS),
                        value(importJobs, ImportJobStatus.FAILED)
                ),
                new ReconciliationJobStatistics(
                        sum(reconciliationJobs),
                        value(
                                reconciliationJobs,
                                ReconciliationJobStatus.PENDING
                        ),
                        value(
                                reconciliationJobs,
                                ReconciliationJobStatus.PROCESSING
                        ),
                        value(
                                reconciliationJobs,
                                ReconciliationJobStatus.COMPLETED
                        ),
                        value(
                                reconciliationJobs,
                                ReconciliationJobStatus.FAILED
                        )
                ),
                new ReconciliationResultStatistics(
                        value(
                                reconciliationResults,
                                ReconciliationResultType.MATCHED
                        ),
                        value(
                                reconciliationResults,
                                ReconciliationResultType.UNMATCHED
                        ),
                        value(
                                reconciliationResults,
                                ReconciliationResultType.DUPLICATE
                        ),
                        value(
                                reconciliationResults,
                                ReconciliationResultType.SUSPICIOUS
                        )
                ),
                new RiskHitStatistics(
                        sum(riskHits),
                        value(riskHits, RiskRuleCode.LARGE_AMOUNT),
                        value(riskHits, RiskRuleCode.POSSIBLE_DUPLICATE),
                        value(
                                riskHits,
                                RiskRuleCode.FREQUENT_TRANSACTION
                        )
                ),
                new ReviewTaskStatistics(
                        value(reviewTasks, ReviewTaskStatus.PENDING),
                        value(reviewTasks, ReviewTaskStatus.CONFIRMED),
                        value(reviewTasks, ReviewTaskStatus.IGNORED)
                )
        );
    }

    private Map<String, Long> counts(
            List<StatusCountRow> rows,
            Class<? extends Enum<?>> enumType) {
        Map<String, Long> result = new HashMap<>();
        for (StatusCountRow row : rows) {
            String category = row.getCategory();
            requireKnownCategory(category, enumType);
            if (row.getCountValue() < 0
                    || result.put(category, row.getCountValue()) != null) {
                throw new IllegalStateException(
                        "Statistics aggregation returned invalid counts"
                );
            }
        }
        return result;
    }

    private void requireKnownCategory(
            String category,
            Class<? extends Enum<?>> enumType) {
        if (category == null) {
            throw new IllegalStateException(
                    "Statistics aggregation returned a null category"
            );
        }
        for (Enum<?> value : enumType.getEnumConstants()) {
            if (value.name().equals(category)) {
                return;
            }
        }
        throw new IllegalStateException(
                "Statistics aggregation returned an unknown category"
        );
    }

    private long value(Map<String, Long> counts, Enum<?> category) {
        return counts.getOrDefault(category.name(), 0L);
    }

    private long sum(Map<String, Long> counts) {
        return counts.values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }
}
