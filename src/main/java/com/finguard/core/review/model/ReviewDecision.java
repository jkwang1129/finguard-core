package com.finguard.core.review.model;

public enum ReviewDecision {
    CONFIRMED,
    IGNORED;

    public ReviewTaskStatus toStatus() {
        return ReviewTaskStatus.valueOf(name());
    }
}
