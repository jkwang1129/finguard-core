package com.finguard.core.reconciliation.model;

import java.util.Objects;

public record ReconciliationDecision(
        Long csvTransactionId,
        Long manualTransactionId,
        ReconciliationResultType resultType,
        ReconciliationMatchMethod matchMethod,
        ReconciliationReasonCode reasonCode
) {

    public ReconciliationDecision {
        if (csvTransactionId == null || csvTransactionId <= 0) {
            throw new IllegalArgumentException(
                    "csvTransactionId must be positive"
            );
        }
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(matchMethod, "matchMethod must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        validateShape(
                manualTransactionId,
                resultType,
                matchMethod,
                reasonCode
        );
    }

    private static void validateShape(
            Long manualTransactionId,
            ReconciliationResultType resultType,
            ReconciliationMatchMethod matchMethod,
            ReconciliationReasonCode reasonCode) {
        if (manualTransactionId != null && manualTransactionId <= 0) {
            throw new IllegalArgumentException(
                    "manualTransactionId must be positive"
            );
        }
        switch (resultType) {
            case MATCHED -> {
                if (manualTransactionId == null
                        || matchMethod
                        == ReconciliationMatchMethod.NONE
                        || !matchedReasonMatches(
                        matchMethod,
                        reasonCode
                )) {
                    throw new IllegalArgumentException(
                            "MATCHED decision shape is invalid"
                    );
                }
            }
            case UNMATCHED -> {
                if (manualTransactionId != null
                        || matchMethod
                        != ReconciliationMatchMethod.NONE
                        || reasonCode
                        != ReconciliationReasonCode.NO_CANDIDATE) {
                    throw new IllegalArgumentException(
                            "UNMATCHED decision shape is invalid"
                    );
                }
            }
            case DUPLICATE -> {
                if (matchMethod
                        != ReconciliationMatchMethod.NONE
                        || (reasonCode
                        != ReconciliationReasonCode.MULTIPLE_CANDIDATES
                        && reasonCode
                        != ReconciliationReasonCode
                        .MANUAL_ALREADY_MATCHED)) {
                    throw new IllegalArgumentException(
                            "DUPLICATE decision shape is invalid"
                    );
                }
            }
            case SUSPICIOUS -> {
                if (manualTransactionId == null
                        || matchMethod
                        != ReconciliationMatchMethod.NONE
                        || (reasonCode
                        != ReconciliationReasonCode.DIRECTION_MISMATCH
                        && reasonCode
                        != ReconciliationReasonCode.AMOUNT_MISMATCH
                        && reasonCode
                        != ReconciliationReasonCode.TIME_OUT_OF_RANGE)) {
                    throw new IllegalArgumentException(
                            "SUSPICIOUS decision shape is invalid"
                    );
                }
            }
        }
    }

    private static boolean matchedReasonMatches(
            ReconciliationMatchMethod matchMethod,
            ReconciliationReasonCode reasonCode) {
        return (matchMethod == ReconciliationMatchMethod.EXACT
                && reasonCode == ReconciliationReasonCode.EXACT_MATCH)
                || (matchMethod
                == ReconciliationMatchMethod.TOLERANCE
                && reasonCode
                == ReconciliationReasonCode.TOLERANCE_MATCH);
    }
}
