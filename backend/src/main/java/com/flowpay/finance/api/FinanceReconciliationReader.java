package com.flowpay.finance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Finance-owned read contract for statement reconciliation candidates. */
public interface FinanceReconciliationReader {
    CandidateSearch matchingMovements(UUID tenantId, UUID accountId, String direction,
                                      long amountMinor, LocalDate from, LocalDate through, int limit);

    Optional<MovementCandidate> findLinkableMovement(UUID tenantId, UUID accountId, UUID movementId);

    record MovementCandidate(UUID id, UUID accountId, String accountName, String direction, String amountMinor,
                             String currency, LocalDate effectiveDate, String origin, String description) {}
    record CandidateSearch(List<MovementCandidate> items, long total) {}
}
