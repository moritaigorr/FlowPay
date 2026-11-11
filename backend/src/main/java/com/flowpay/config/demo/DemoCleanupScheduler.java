package com.flowpay.config.demo;

import com.flowpay.company.CompanyProvisioning;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DemoCleanupScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(DemoCleanupScheduler.class);
    private final CompanyProvisioning companies;
    private final DemoCleanupWorker cleaner;
    private final java.time.Clock clock;
    private final int batchSize;

    public DemoCleanupScheduler(CompanyProvisioning companies, DemoCleanupWorker cleaner,
            java.time.Clock clock, @Value("${flowpay.demo.cleanup-batch-size:50}") int batchSize) {
        this.companies = companies;
        this.cleaner = cleaner;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${flowpay.demo.cleanup-interval-ms:60000}")
    public void cleanExpiredDemos() {
        Instant cutoff = clock.instant();
        for (UUID companyId : companies.expiredDemoIds(cutoff, batchSize)) {
            try {
                cleaner.cleanOne(companyId, cutoff);
            } catch (RuntimeException exception) {
                LOG.warn("Expired demo cleanup failed for company {} ({})", companyId, exception.getClass().getSimpleName());
            }
        }
    }
}
