package com.finguard.core.statistics.controller;

import com.finguard.core.statistics.service.StatisticsOverviewService;
import com.finguard.core.statistics.vo.StatisticsOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@Tag(name = "Statistics", description = "Cached business overview")
public class StatisticsController {

    private final StatisticsOverviewService statisticsOverviewService;

    public StatisticsController(
            StatisticsOverviewService statisticsOverviewService) {
        this.statisticsOverviewService = statisticsOverviewService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get the business statistics overview")
    public StatisticsOverviewResponse overview() {
        return statisticsOverviewService.getOverview();
    }
}
