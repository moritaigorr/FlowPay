package com.flowpay.identity;

import java.util.UUID;

public interface IdentityAccess {
    OperatorView authenticate(String email, String password);
    OperatorView requireOperator(UUID userId, UUID companyId);
}
