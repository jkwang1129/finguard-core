package com.finguard.core.reconciliation.service;

import com.finguard.core.reconciliation.model.ReconciliationDecision;
import com.finguard.core.reconciliation.model.ReconciliationMatchMethod;
import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.model.ReconciliationTransaction;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReconciliationMatcher {

    public static final int TIME_TOLERANCE_DAYS = 3;

    public List<ReconciliationDecision> match(
            List<ReconciliationTransaction> csvTransactions,
            List<ReconciliationTransaction> manualTransactions) {
        List<ReconciliationTransaction> csv =
                sortedUnique(csvTransactions, "CSV");
        List<ReconciliationTransaction> manual =
                sortedUnique(manualTransactions, "MANUAL");

        Map<BusinessKey, ReconciliationTransaction> manualByKey =
                indexManualByBusinessKey(manual);
        Map<Long, List<ReconciliationTransaction>> manualByAccount =
                indexManualByAccount(manual);
        Map<Long, ReconciliationDecision> decisions =
                new LinkedHashMap<>();
        Set<Long> reservedManualIds = new HashSet<>();

        for (ReconciliationTransaction external : csv) {
            ReconciliationTransaction strong = manualByKey.get(
                    BusinessKey.of(external)
            );
            if (strong == null) {
                continue;
            }
            reservedManualIds.add(strong.id());
            decisions.put(
                    external.id(),
                    decideStrong(external, strong)
            );
        }

        Set<Long> claimedManualIds = new HashSet<>();
        decisions.values().stream()
                .filter(decision -> decision.resultType()
                        == ReconciliationResultType.MATCHED)
                .map(ReconciliationDecision::manualTransactionId)
                .forEach(claimedManualIds::add);

        for (ReconciliationTransaction external : csv) {
            if (decisions.containsKey(external.id())) {
                continue;
            }
            List<ReconciliationTransaction> eligible =
                    manualByAccount.getOrDefault(
                                    external.accountId(),
                                    List.of()
                            )
                            .stream()
                            .filter(candidate -> weaklyMatches(
                                    external,
                                    candidate
                            ))
                            .toList();
            decisions.put(
                    external.id(),
                    decideWeak(
                            external,
                            eligible,
                            reservedManualIds,
                            claimedManualIds
                    )
            );
        }

        return csv.stream()
                .map(transaction -> decisions.get(transaction.id()))
                .toList();
    }

    private List<ReconciliationTransaction> sortedUnique(
            List<ReconciliationTransaction> transactions,
            String label) {
        if (transactions == null) {
            throw new IllegalArgumentException(
                    label + " transactions must not be null"
            );
        }
        List<ReconciliationTransaction> sorted = transactions.stream()
                .sorted(Comparator.comparing(
                        ReconciliationTransaction::id
                ))
                .toList();
        Set<Long> ids = new HashSet<>();
        for (ReconciliationTransaction transaction : sorted) {
            if (transaction == null || !ids.add(transaction.id())) {
                throw new IllegalArgumentException(
                        label + " transaction ids must be unique"
                );
            }
        }
        return sorted;
    }

    private Map<BusinessKey, ReconciliationTransaction>
    indexManualByBusinessKey(
            List<ReconciliationTransaction> manualTransactions) {
        Map<BusinessKey, ReconciliationTransaction> index =
                new HashMap<>();
        for (ReconciliationTransaction transaction
                : manualTransactions) {
            ReconciliationTransaction previous = index.put(
                    BusinessKey.of(transaction),
                    transaction
            );
            if (previous != null) {
                throw new IllegalArgumentException(
                        "MANUAL business keys must be unique"
                );
            }
        }
        return index;
    }

    private Map<Long, List<ReconciliationTransaction>>
    indexManualByAccount(
            List<ReconciliationTransaction> manualTransactions) {
        Map<Long, List<ReconciliationTransaction>> index =
                new HashMap<>();
        for (ReconciliationTransaction transaction
                : manualTransactions) {
            index.computeIfAbsent(
                    transaction.accountId(),
                    ignored -> new ArrayList<>()
            ).add(transaction);
        }
        index.values().forEach(list -> list.sort(
                Comparator.comparing(ReconciliationTransaction::id)
        ));
        return index;
    }

    private ReconciliationDecision decideStrong(
            ReconciliationTransaction csv,
            ReconciliationTransaction manual) {
        if (csv.direction() != manual.direction()) {
            return suspicious(
                    csv,
                    manual,
                    ReconciliationReasonCode.DIRECTION_MISMATCH
            );
        }
        if (csv.amount().compareTo(manual.amount()) != 0) {
            return suspicious(
                    csv,
                    manual,
                    ReconciliationReasonCode.AMOUNT_MISMATCH
            );
        }
        if (!withinTolerance(
                csv.transactionTime(),
                manual.transactionTime()
        )) {
            return suspicious(
                    csv,
                    manual,
                    ReconciliationReasonCode.TIME_OUT_OF_RANGE
            );
        }
        boolean exactTime = csv.transactionTime()
                .equals(manual.transactionTime());
        return new ReconciliationDecision(
                csv.id(),
                manual.id(),
                ReconciliationResultType.MATCHED,
                exactTime
                        ? ReconciliationMatchMethod.EXACT
                        : ReconciliationMatchMethod.TOLERANCE,
                exactTime
                        ? ReconciliationReasonCode.EXACT_MATCH
                        : ReconciliationReasonCode.TOLERANCE_MATCH
        );
    }

    private ReconciliationDecision decideWeak(
            ReconciliationTransaction csv,
            List<ReconciliationTransaction> eligible,
            Set<Long> reservedManualIds,
            Set<Long> claimedManualIds) {
        if (eligible.isEmpty()) {
            return new ReconciliationDecision(
                    csv.id(),
                    null,
                    ReconciliationResultType.UNMATCHED,
                    ReconciliationMatchMethod.NONE,
                    ReconciliationReasonCode.NO_CANDIDATE
            );
        }
        if (eligible.size() > 1) {
            return new ReconciliationDecision(
                    csv.id(),
                    null,
                    ReconciliationResultType.DUPLICATE,
                    ReconciliationMatchMethod.NONE,
                    ReconciliationReasonCode.MULTIPLE_CANDIDATES
            );
        }
        ReconciliationTransaction candidate = eligible.get(0);
        if (reservedManualIds.contains(candidate.id())
                || claimedManualIds.contains(candidate.id())) {
            return new ReconciliationDecision(
                    csv.id(),
                    candidate.id(),
                    ReconciliationResultType.DUPLICATE,
                    ReconciliationMatchMethod.NONE,
                    ReconciliationReasonCode.MANUAL_ALREADY_MATCHED
            );
        }
        claimedManualIds.add(candidate.id());
        return new ReconciliationDecision(
                csv.id(),
                candidate.id(),
                ReconciliationResultType.MATCHED,
                ReconciliationMatchMethod.TOLERANCE,
                ReconciliationReasonCode.TOLERANCE_MATCH
        );
    }

    private ReconciliationDecision suspicious(
            ReconciliationTransaction csv,
            ReconciliationTransaction manual,
            ReconciliationReasonCode reasonCode) {
        return new ReconciliationDecision(
                csv.id(),
                manual.id(),
                ReconciliationResultType.SUSPICIOUS,
                ReconciliationMatchMethod.NONE,
                reasonCode
        );
    }

    private boolean weaklyMatches(
            ReconciliationTransaction csv,
            ReconciliationTransaction manual) {
        return csv.accountId().equals(manual.accountId())
                && csv.direction() == manual.direction()
                && csv.amount().compareTo(manual.amount()) == 0
                && withinTolerance(
                csv.transactionTime(),
                manual.transactionTime()
        );
    }

    private boolean withinTolerance(
            LocalDateTime csvTime,
            LocalDateTime manualTime) {
        return !manualTime.isBefore(
                csvTime.minusDays(TIME_TOLERANCE_DAYS)
        ) && !manualTime.isAfter(
                csvTime.plusDays(TIME_TOLERANCE_DAYS)
        );
    }

    private record BusinessKey(
            Long accountId,
            String externalTransactionNo
    ) {
        static BusinessKey of(
                ReconciliationTransaction transaction) {
            return new BusinessKey(
                    transaction.accountId(),
                    transaction.externalTransactionNo()
            );
        }
    }
}
