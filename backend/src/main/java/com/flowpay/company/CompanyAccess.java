package com.flowpay.company;

import java.util.UUID;
import java.time.Instant;
import java.util.Optional;

public interface CompanyAccess {
    CompanyView requireActive(UUID companyId);
    CompanyView lockActive(UUID companyId);
    CompanyView lockForCleanup(UUID companyId);
    Optional<CompanyView> lockExpiredDemo(UUID companyId, Instant cutoff);
}
