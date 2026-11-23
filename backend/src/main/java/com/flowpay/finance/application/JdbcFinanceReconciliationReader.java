package com.flowpay.finance.application;

import com.flowpay.finance.api.FinanceReconciliationReader;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcFinanceReconciliationReader implements FinanceReconciliationReader {
    private final JdbcTemplate jdbc;

    public JdbcFinanceReconciliationReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public CandidateSearch matchingMovements(UUID tenantId, UUID accountId, String direction,
                                              long amountMinor, LocalDate from, LocalDate through, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        long total = jdbc.queryForObject("""
                SELECT count(*) FROM cash_transaction
                WHERE tenant_id = ? AND financial_account_id = ? AND direction = ? AND amount_minor = ?
                  AND effective_date BETWEEN ? AND ? AND origin <> 'OPENING_BALANCE'
                """, Long.class, tenantId, accountId, direction, amountMinor, from, through);
        var items = jdbc.query("""
                SELECT ct.id, ct.financial_account_id, a.name AS account_name, ct.direction,
                       ct.amount_minor::text AS amount_minor, ct.currency, ct.effective_date, ct.origin, ct.description
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                WHERE ct.tenant_id = ? AND ct.financial_account_id = ?
                  AND ct.direction = ? AND ct.amount_minor = ?
                  AND ct.effective_date BETWEEN ? AND ?
                  AND ct.origin <> 'OPENING_BALANCE'
                ORDER BY abs(ct.effective_date - ?), ct.created_at DESC, ct.id
                LIMIT ?
                """, (rs, row) -> new MovementCandidate(rs.getObject("id", UUID.class),
                rs.getObject("financial_account_id", UUID.class), rs.getString("account_name"),
                rs.getString("direction"), rs.getString("amount_minor"), rs.getString("currency"), rs.getDate("effective_date").toLocalDate(),
                rs.getString("origin"), rs.getString("description")), tenantId, accountId, direction, amountMinor,
                from, through, from.plusDays(java.time.temporal.ChronoUnit.DAYS.between(from, through) / 2), boundedLimit);
        return new CandidateSearch(items, total);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<MovementCandidate> findLinkableMovement(UUID tenantId, UUID accountId, UUID movementId) {
        return jdbc.query("""
                SELECT ct.id, ct.financial_account_id, a.name AS account_name, ct.direction,
                       ct.amount_minor::text AS amount_minor, ct.currency, ct.effective_date, ct.origin, ct.description
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                WHERE ct.tenant_id = ? AND ct.financial_account_id = ? AND ct.id = ?
                  AND ct.origin <> 'OPENING_BALANCE'
                """, (rs, row) -> new MovementCandidate(rs.getObject("id", UUID.class),
                rs.getObject("financial_account_id", UUID.class), rs.getString("account_name"),
                rs.getString("direction"), rs.getString("amount_minor"), rs.getString("currency"),
                rs.getDate("effective_date").toLocalDate(), rs.getString("origin"), rs.getString("description")),
                tenantId, accountId, movementId).stream().findFirst();
    }
}
