package com.finguard.core.statistics.vo;

public record ReviewTaskStatistics(
        long pending,
        long confirmed,
        long ignored) {
}
