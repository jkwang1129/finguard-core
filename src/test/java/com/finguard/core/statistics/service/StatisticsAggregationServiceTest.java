package com.finguard.core.statistics.service;

import com.finguard.core.statistics.mapper.StatisticsMapper;
import com.finguard.core.statistics.model.StatusCountRow;
import com.finguard.core.statistics.vo.StatisticsOverviewResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsAggregationServiceTest {

    private final StatisticsMapper mapper = mock(StatisticsMapper.class);

    private final StatisticsAggregationService service =
            new StatisticsAggregationService(
                    mapper,
                    Clock.fixed(
                            Instant.parse("2026-08-03T08:00:00Z"),
                            ZoneId.of("Asia/Shanghai")
                    )
            );

    @Test
    void mapsEveryKnownCategoryAndFillsMissingCategoriesWithZero() {
        when(mapper.countImportJobsByStatus()).thenReturn(List.of(
                row("PENDING", 1),
                row("SUCCESS", 3),
                row("FAILED", 2)
        ));
        when(mapper.countReconciliationJobsByStatus()).thenReturn(List.of(
                row("PROCESSING", 2),
                row("COMPLETED", 4)
        ));
        when(mapper.countReconciliationResultsByType()).thenReturn(List.of(
                row("MATCHED", 5),
                row("SUSPICIOUS", 1)
        ));
        when(mapper.countRiskHitsByRule()).thenReturn(List.of(
                row("LARGE_AMOUNT", 2),
                row("FREQUENT_TRANSACTION", 3)
        ));
        when(mapper.countReviewTasksByStatus()).thenReturn(List.of(
                row("PENDING", 4),
                row("IGNORED", 1)
        ));

        StatisticsOverviewResponse response = service.aggregate();

        assertThat(response.generatedAt().toString())
                .isEqualTo("2026-08-03T16:00+08:00");
        assertThat(response.importJobs().total()).isEqualTo(6);
        assertThat(response.importJobs().processing()).isZero();
        assertThat(response.importJobs().partialSuccess()).isZero();
        assertThat(response.reconciliationJobs().total()).isEqualTo(6);
        assertThat(response.reconciliationJobs().pending()).isZero();
        assertThat(response.reconciliationResults().matched()).isEqualTo(5);
        assertThat(response.reconciliationResults().unmatched()).isZero();
        assertThat(response.riskHits().total()).isEqualTo(5);
        assertThat(response.riskHits().possibleDuplicate()).isZero();
        assertThat(response.reviewTasks().pending()).isEqualTo(4);
        assertThat(response.reviewTasks().confirmed()).isZero();
        assertThat(response.reviewTasks().ignored()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownDatabaseCategory() {
        when(mapper.countImportJobsByStatus()).thenReturn(List.of(
                row("UNKNOWN", 1)
        ));

        assertThatThrownBy(service::aggregate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown category");
    }

    private StatusCountRow row(String category, long count) {
        StatusCountRow row = new StatusCountRow();
        row.setCategory(category);
        row.setCountValue(count);
        return row;
    }
}
