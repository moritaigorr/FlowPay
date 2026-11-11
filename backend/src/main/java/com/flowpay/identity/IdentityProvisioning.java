package com.flowpay.identity;

import java.time.Instant;
import java.util.UUID;

public interface IdentityProvisioning {
    OperatorView create(NewOperator command);
    void deleteExpiredDemoOperators(UUID companyId, Instant cutoff);
}
