package com.flowpay.statements.api;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Public statement-line contract for reconciliation without exposing statement persistence. */
public interface StatementLineReader {
    Optional<StatementLine> findLine(UUID tenantId, UUID lineId);

    /** Locks the line row until the caller's transaction completes. */
    Optional<StatementLine> lockLine(UUID tenantId, UUID lineId);

    record StatementLine(UUID id, UUID accountId, LocalDate date, String description, String direction,
                         String amountMinor, String status, String reviewStatus) {}
}
