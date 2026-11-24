package com.flowpay.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowpay.identity.api.TenantRole;
import org.junit.jupiter.api.Test;

class TenantRoleTest {
    @Test
    void ownerAndManagerCanManageBusinessDataButReaderAndUnknownRolesCannot() {
        assertThat(TenantRole.canManageBusinessData("OWNER")).isTrue();
        assertThat(TenantRole.canManageBusinessData("MANAGER")).isTrue();
        assertThat(TenantRole.canManageBusinessData("READER")).isFalse();
        assertThat(TenantRole.canManageBusinessData("UNKNOWN")).isFalse();
        assertThat(TenantRole.canManageBusinessData(null)).isFalse();
    }
}
