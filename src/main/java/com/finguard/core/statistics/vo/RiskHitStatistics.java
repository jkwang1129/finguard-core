package com.finguard.core.statistics.vo;

public record RiskHitStatistics(
        long total,
        long largeAmount,
        long possibleDuplicate,
        long frequentTransaction) {
}
