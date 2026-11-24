package com.flowpay.identity.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthenticationRateLimitCleanup {
    private final JdbcTemplate jdbc;

    public AuthenticationRateLimitCleanup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    @Transactional
    public void deleteExpiredBuckets() {
        jdbc.update("DELETE FROM authentication_rate_limit WHERE updated_at < clock_timestamp() - INTERVAL '2 days'");
    }
}
