package com.flowpay.finance.api;

import java.time.LocalDate;
import java.util.UUID;

/** Finance-owned command for creating a cash movement as part of statement reconciliation. */
public interface FinanceReconciliationWriter {
    FinanceReconciliationReader.MovementCandidate createFromStatement(UUID tenantId, UUID accountId, String direction,
                                                                        long amountMinor, LocalDate effectiveDate,
                                                                        String description, UUID categoryId);
}
