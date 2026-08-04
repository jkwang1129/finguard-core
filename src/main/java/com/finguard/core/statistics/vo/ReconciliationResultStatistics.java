package com.finguard.core.statistics.vo;

public record ReconciliationResultStatistics(
        long matched,
        long unmatched,
        long duplicate,
        long suspicious) {
}
