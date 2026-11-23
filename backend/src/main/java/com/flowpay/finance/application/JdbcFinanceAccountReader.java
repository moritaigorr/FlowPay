package com.flowpay.finance.application;

import com.flowpay.finance.api.FinanceAccountReader;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcFinanceAccountReader implements FinanceAccountReader {
    private final JdbcTemplate jdbc;

    public JdbcFinanceAccountReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean isActiveAccount(UUID tenantId, UUID accountId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM financial_account WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE')
                """, Boolean.class, tenantId, accountId));
    }
}
