package com.finguard.core.statistics.service;

import com.finguard.core.statistics.cache.StatisticsOverviewCache;
import com.finguard.core.statistics.vo.StatisticsOverviewResponse;
import org.springframework.stereotype.Service;

@Service
public class StatisticsOverviewService {

    private final StatisticsOverviewCache cache;
    private final StatisticsAggregationService aggregationService;

    public StatisticsOverviewService(
            StatisticsOverviewCache cache,
            StatisticsAggregationService aggregationService) {
        this.cache = cache;
        this.aggregationService = aggregationService;
    }

    public StatisticsOverviewResponse getOverview() {
        return cache.get().orElseGet(() -> {
            StatisticsOverviewResponse response =
                    aggregationService.aggregate();
            cache.put(response);
            return response;
        });
    }
}
