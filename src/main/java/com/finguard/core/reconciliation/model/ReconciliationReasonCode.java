package com.finguard.core.reconciliation.model;

public enum ReconciliationReasonCode {
    EXACT_MATCH,
    TOLERANCE_MATCH,
    NO_CANDIDATE,
    MULTIPLE_CANDIDATES,
    MANUAL_ALREADY_MATCHED,
    DIRECTION_MISMATCH,
    AMOUNT_MISMATCH,
    TIME_OUT_OF_RANGE
}
