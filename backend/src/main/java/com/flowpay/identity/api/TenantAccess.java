package com.flowpay.identity.api;

import java.util.UUID;

public interface TenantAccess {
    CurrentTenant requireSingleTenant(String email);
    java.util.List<CurrentTenant> listTenants(String email);

    /** Optional identity detail needed by audited commands; simple tenant-only consumers need not resolve it. */
    default UUID requireUserId(String email) {
        throw new UnsupportedOperationException("User identity lookup is not configured.");
    }

    record CurrentTenant(UUID id, String name, String role) {}
}
