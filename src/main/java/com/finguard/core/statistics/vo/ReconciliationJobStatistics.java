package com.finguard.core.statistics.vo;

public record ReconciliationJobStatistics(
        long total,
        long pending,
        long processing,
        long completed,
        long failed) {
}
