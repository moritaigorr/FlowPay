package com.flowpay.finance.api;

import java.util.UUID;

/** Public account authorization contract for other modules. */
public interface FinanceAccountReader {
    boolean isActiveAccount(UUID tenantId, UUID accountId);
}
