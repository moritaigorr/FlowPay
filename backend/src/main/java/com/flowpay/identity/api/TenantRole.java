package com.flowpay.identity.api;

/** Roles shared by modules when enforcing tenant-level business permissions. */
public enum TenantRole {
    OWNER,
    MANAGER,
    READER;

    public static boolean canManageBusinessData(String role) {
        return OWNER.name().equals(role) || MANAGER.name().equals(role);
    }
}
