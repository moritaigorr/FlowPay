package com.flowpay.identity.api;

import java.util.UUID;

public interface TenantAccess {
    CurrentTenant requireSingleTenant(String email);

    record CurrentTenant(UUID id, String name, String role) {}
}
