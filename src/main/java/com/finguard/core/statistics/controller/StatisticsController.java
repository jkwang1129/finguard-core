package com.finguard.core.statistics.controller;

import com.finguard.core.statistics.service.StatisticsOverviewService;
import com.finguard.core.statistics.vo.StatisticsOverviewResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsOverviewService statisticsOverviewService;

    public StatisticsController(
            StatisticsOverviewService statisticsOverviewService) {
        this.statisticsOverviewService = statisticsOverviewService;
    }

    @GetMapping("/overview")
    public StatisticsOverviewResponse overview() {
        return statisticsOverviewService.getOverview();
    }
}
