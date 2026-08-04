package com.finguard.core.statistics.vo;

public record ImportJobStatistics(
        long total,
        long pending,
        long processing,
        long success,
        long partialSuccess,
        long failed) {
}
