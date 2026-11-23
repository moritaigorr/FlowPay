package com.flowpay.statements.application;

import com.flowpay.statements.api.StatementLineReader;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcStatementLineReader implements StatementLineReader {
    private final JdbcTemplate jdbc;

    public JdbcStatementLineReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<StatementLine> findLine(UUID tenantId, UUID lineId) {
        return jdbc.query(query(false), JdbcStatementLineReader::map, tenantId, lineId).stream().findFirst();
    }

    @Override
    @Transactional
    public java.util.Optional<StatementLine> lockLine(UUID tenantId, UUID lineId) {
        return jdbc.query(query(true), JdbcStatementLineReader::map, tenantId, lineId).stream().findFirst();
    }

    private static String query(boolean lock) {
        return """
                SELECT sl.id, si.financial_account_id, sl.effective_date, sl.description, sl.direction,
                       sl.amount_minor::text AS amount_minor, sl.row_status, sl.review_status
                FROM statement_line sl
                JOIN statement_import si ON si.tenant_id = sl.tenant_id AND si.id = sl.statement_import_id
                WHERE sl.tenant_id = ? AND sl.id = ?
                """ + (lock ? " FOR UPDATE OF sl" : "");
    }

    private static StatementLine map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var date = rs.getDate("effective_date");
        return new StatementLine(rs.getObject("id", UUID.class), rs.getObject("financial_account_id", UUID.class),
                date == null ? null : date.toLocalDate(), rs.getString("description"), rs.getString("direction"),
                rs.getString("amount_minor"), rs.getString("row_status"), rs.getString("review_status"));
    }
}
