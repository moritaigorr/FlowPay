package com.flowpay.reconciliation.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Public read contract for statement pages to annotate already reconciled lines. */
public interface ReconciliationMatchStatusReader {
    List<ActiveMatch> activeMatches(UUID tenantId, List<UUID> statementLineIds);

    Set<UUID> activeMovementIds(UUID tenantId, List<UUID> movementIds);

    record ActiveMatch(UUID statementLineId, UUID matchId, String decision) {}
}
