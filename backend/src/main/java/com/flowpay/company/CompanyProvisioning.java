package com.flowpay.company;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyProvisioning {
    CompanyView create(NewCompany command);
    Optional<CompanyView> createDemoIfAvailable(int activeCapacity);
    List<UUID> expiredDemoIds(Instant cutoff, int limit);
    void deleteExpiredDemo(UUID companyId, Instant cutoff);
}
