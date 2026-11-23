package com.flowpay.reconciliation.application;

import com.flowpay.reconciliation.api.ReconciliationMatchStatusReader;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcReconciliationMatchStatusReader implements ReconciliationMatchStatusReader {
    private final JdbcTemplate jdbc;

    public JdbcReconciliationMatchStatusReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveMatch> activeMatches(UUID tenantId, List<UUID> statementLineIds) {
        if (statementLineIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(statementLineIds.size(), "?"));
        var args = new Object[statementLineIds.size() + 1];
        args[0] = tenantId;
        for (int i = 0; i < statementLineIds.size(); i++) args[i + 1] = statementLineIds.get(i);
        String sql = "SELECT statement_line_id, id, decision FROM reconciliation_match "
                + "WHERE tenant_id = ? AND reversed_at IS NULL AND statement_line_id IN (" + placeholders + ")";
        return jdbc.query(sql, (rs, row) -> new ActiveMatch(rs.getObject("statement_line_id", UUID.class),
                rs.getObject("id", UUID.class), rs.getString("decision")), args);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> activeMovementIds(UUID tenantId, List<UUID> movementIds) {
        if (movementIds.isEmpty()) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(movementIds.size(), "?"));
        var args = new Object[movementIds.size() + 1];
        args[0] = tenantId;
        for (int i = 0; i < movementIds.size(); i++) args[i + 1] = movementIds.get(i);
        String sql = "SELECT cash_transaction_id FROM reconciliation_match "
                + "WHERE tenant_id = ? AND reversed_at IS NULL AND cash_transaction_id IN (" + placeholders + ")";
        return Set.copyOf(jdbc.query(sql, (rs, row) -> rs.getObject("cash_transaction_id", UUID.class), args));
    }
}
