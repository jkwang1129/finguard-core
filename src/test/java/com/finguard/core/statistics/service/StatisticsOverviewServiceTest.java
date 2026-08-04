package com.finguard.core.statistics.service;

import com.finguard.core.statistics.cache.StatisticsOverviewCache;
import com.finguard.core.statistics.vo.ImportJobStatistics;
import com.finguard.core.statistics.vo.ReconciliationJobStatistics;
import com.finguard.core.statistics.vo.ReconciliationResultStatistics;
import com.finguard.core.statistics.vo.ReviewTaskStatistics;
import com.finguard.core.statistics.vo.RiskHitStatistics;
import com.finguard.core.statistics.vo.StatisticsOverviewResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsOverviewServiceTest {

    private final StatisticsOverviewCache cache =
            mock(StatisticsOverviewCache.class);

    private final StatisticsAggregationService aggregationService =
            mock(StatisticsAggregationService.class);

    private final StatisticsOverviewService service =
            new StatisticsOverviewService(cache, aggregationService);

    @Test
    void cacheHitReturnsSnapshotWithoutDatabaseAggregation() {
        StatisticsOverviewResponse cached = response();
        when(cache.get()).thenReturn(Optional.of(cached));

        assertThat(service.getOverview()).isSameAs(cached);
        verify(aggregationService, never()).aggregate();
        verify(cache, never()).put(cached);
    }

    @Test
    void cacheMissAggregatesMysqlAndCachesSnapshot() {
        StatisticsOverviewResponse aggregated = response();
        when(cache.get()).thenReturn(Optional.empty());
        when(aggregationService.aggregate()).thenReturn(aggregated);

        assertThat(service.getOverview()).isSameAs(aggregated);
        verify(cache).put(aggregated);
    }

    private StatisticsOverviewResponse response() {
        return new StatisticsOverviewResponse(
                OffsetDateTime.parse("2026-08-03T16:00:00+08:00"),
                new ImportJobStatistics(0, 0, 0, 0, 0, 0),
                new ReconciliationJobStatistics(0, 0, 0, 0, 0),
                new ReconciliationResultStatistics(0, 0, 0, 0),
                new RiskHitStatistics(0, 0, 0, 0),
                new ReviewTaskStatistics(0, 0, 0)
        );
    }
}
