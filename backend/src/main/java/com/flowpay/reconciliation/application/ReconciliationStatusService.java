package com.flowpay.reconciliation.application;

import com.flowpay.identity.api.TenantAccess;
import com.flowpay.reconciliation.api.ReconciliationMatchStatusReader;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReconciliationStatusService {
    private static final int MAX_LINE_IDS = 100;
    private final TenantAccess tenantAccess;
    private final ReconciliationMatchStatusReader matches;

    public ReconciliationStatusService(TenantAccess tenantAccess, ReconciliationMatchStatusReader matches) {
        this.tenantAccess = tenantAccess;
        this.matches = matches;
    }

    @Transactional(readOnly = true)
    public List<ReconciliationMatchStatusReader.ActiveMatch> activeMatches(String email, List<UUID> lineIds) {
        if (lineIds == null || lineIds.size() > MAX_LINE_IDS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A consulta aceita até 100 linhas por vez.");
        var tenant = tenantAccess.requireSingleTenant(email);
        return matches.activeMatches(tenant.id(), lineIds.stream().distinct().toList());
    }
}
