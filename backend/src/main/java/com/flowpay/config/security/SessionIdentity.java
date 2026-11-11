package com.flowpay.config.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record SessionIdentity(UUID userId, UUID companyId) implements Principal, Serializable {
    @Override
    public String getName() {
        return userId.toString();
    }
}
