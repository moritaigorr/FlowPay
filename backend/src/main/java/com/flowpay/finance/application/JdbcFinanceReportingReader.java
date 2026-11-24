package com.flowpay.finance.application;

import com.flowpay.finance.api.FinanceReportingReader;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JdbcFinanceReportingReader implements FinanceReportingReader {
    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;

    public JdbcFinanceReportingReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public CashSummary cashSummary(UUID tenantId, LocalDate from, LocalDate to, LocalDate asOf) {
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(amount_minor) FILTER (WHERE origin NOT IN ('OPENING_BALANCE', 'TRANSFER', 'ADJUSTMENT') AND direction = 'INFLOW' AND effective_date BETWEEN ? AND ?), 0)::text AS inflows,
                       COALESCE(sum(amount_minor) FILTER (WHERE origin NOT IN ('OPENING_BALANCE', 'TRANSFER', 'ADJUSTMENT') AND direction = 'OUTFLOW' AND effective_date BETWEEN ? AND ?), 0)::text AS outflows,
                       COALESCE(sum(CASE direction WHEN 'INFLOW' THEN amount_minor ELSE -amount_minor END)
                                FILTER (WHERE effective_date <= ?), 0)::text AS balance
                FROM cash_transaction
                WHERE tenant_id = ?
                """, (rs, row) -> new CashSummary(rs.getString("balance"), rs.getString("inflows"), rs.getString("outflows")),
                from, to, from, to, asOf, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public CashSummary accountCashSummary(UUID tenantId, UUID accountId, LocalDate from, LocalDate to, LocalDate asOf) {
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(amount_minor) FILTER (WHERE origin NOT IN ('OPENING_BALANCE', 'TRANSFER', 'ADJUSTMENT') AND direction = 'INFLOW' AND effective_date BETWEEN ? AND ?), 0)::text AS inflows,
                       COALESCE(sum(amount_minor) FILTER (WHERE origin NOT IN ('OPENING_BALANCE', 'TRANSFER', 'ADJUSTMENT') AND direction = 'OUTFLOW' AND effective_date BETWEEN ? AND ?), 0)::text AS outflows,
                       COALESCE(sum(CASE direction WHEN 'INFLOW' THEN amount_minor ELSE -amount_minor END)
                                FILTER (WHERE effective_date <= ?), 0)::text AS balance
                FROM cash_transaction
                WHERE tenant_id = ? AND financial_account_id = ?
                """, (rs, row) -> new CashSummary(rs.getString("balance"), rs.getString("inflows"), rs.getString("outflows")),
                from, to, from, to, asOf, tenantId, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public TitleSummary titleSummary(UUID tenantId, LocalDate dueFrom, LocalDate dueTo, LocalDate asOf) {
        return jdbc.queryForObject(TITLE_BALANCE_CTE + """
                SELECT COALESCE(sum(remaining_minor) FILTER (WHERE kind = 'RECEIVABLE' AND due_date BETWEEN ? AND ? AND remaining_minor > 0), 0)::text AS receivables_due,
                       count(*) FILTER (WHERE kind = 'RECEIVABLE' AND due_date BETWEEN ? AND ? AND remaining_minor > 0) AS receivables_due_count,
                       COALESCE(sum(remaining_minor) FILTER (WHERE kind = 'PAYABLE' AND due_date BETWEEN ? AND ? AND remaining_minor > 0), 0)::text AS payables_due,
                       count(*) FILTER (WHERE kind = 'PAYABLE' AND due_date BETWEEN ? AND ? AND remaining_minor > 0) AS payables_due_count,
                       COALESCE(sum(remaining_minor) FILTER (WHERE kind = 'RECEIVABLE' AND due_date < ? AND remaining_minor > 0), 0)::text AS overdue_receivables,
                       count(*) FILTER (WHERE kind = 'RECEIVABLE' AND due_date < ? AND remaining_minor > 0) AS overdue_receivables_count,
                       COALESCE(sum(remaining_minor) FILTER (WHERE kind = 'PAYABLE' AND due_date < ? AND remaining_minor > 0), 0)::text AS overdue_payables,
                       count(*) FILTER (WHERE kind = 'PAYABLE' AND due_date < ? AND remaining_minor > 0) AS overdue_payables_count
                FROM title_balances
                """, (rs, row) -> new TitleSummary(rs.getString("receivables_due"), rs.getLong("receivables_due_count"),
                        rs.getString("payables_due"), rs.getLong("payables_due_count"),
                        rs.getString("overdue_receivables"), rs.getLong("overdue_receivables_count"),
                        rs.getString("overdue_payables"), rs.getLong("overdue_payables_count")),
                titleBalanceArguments(tenantId, asOf, dueFrom, dueTo, dueFrom, dueTo, dueFrom, dueTo, dueFrom, dueTo, asOf, asOf, asOf, asOf));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryFlow> cashFlowsByCategory(UUID tenantId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT ct.category_id, COALESCE(cat.name, 'Sem categoria') AS category_name,
                       ct.direction, sum(ct.amount_minor)::text AS amount_minor, count(*) AS movement_count
                FROM cash_transaction ct
                LEFT JOIN financial_category cat ON cat.tenant_id = ct.tenant_id AND cat.id = ct.category_id
                WHERE ct.tenant_id = ? AND ct.origin NOT IN ('OPENING_BALANCE', 'TRANSFER', 'ADJUSTMENT') AND ct.effective_date BETWEEN ? AND ?
                GROUP BY ct.category_id, COALESCE(cat.name, 'Sem categoria'), ct.direction
                ORDER BY ct.direction, lower(COALESCE(cat.name, 'Sem categoria')), ct.category_id NULLS FIRST
                """, (rs, row) -> new CategoryFlow(rs.getObject("category_id", UUID.class), rs.getString("category_name"),
                rs.getString("direction"), rs.getString("amount_minor"), rs.getLong("movement_count")), tenantId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutstandingTitle> upcomingTitles(UUID tenantId, LocalDate after, LocalDate through, LocalDate asOf, int limit) {
        var boundedLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return jdbc.query(TITLE_BALANCE_CTE + """
                SELECT id, kind, description, contact_name, due_date, remaining_minor::text AS remaining_minor
                FROM title_balances
                WHERE due_date > ? AND due_date <= ? AND remaining_minor > 0
                ORDER BY due_date, lower(description), id
                LIMIT ?
                """, (rs, row) -> new OutstandingTitle(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("description"),
                rs.getString("contact_name"), rs.getDate("due_date").toLocalDate(), rs.getString("remaining_minor")),
                titleBalanceArguments(tenantId, asOf, after, through, boundedLimit));
    }

    @Override
    @Transactional(readOnly = true)
    public MovementPage movements(UUID tenantId, LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Movement pagination is outside supported bounds.");
        }
        var total = jdbc.queryForObject("SELECT count(*) FROM cash_transaction WHERE tenant_id = ? AND effective_date BETWEEN ? AND ?",
                Long.class, tenantId, from, to);
        var items = jdbc.query("""
                SELECT ct.id, ct.direction, ct.amount_minor::text AS amount_minor, ct.effective_date,
                       ct.origin, ct.description, ct.category_id, COALESCE(cat.name, 'Sem categoria') AS category_name,
                       ct.financial_account_id, a.name AS account_name
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                LEFT JOIN financial_category cat ON cat.tenant_id = ct.tenant_id AND cat.id = ct.category_id
                WHERE ct.tenant_id = ? AND ct.effective_date BETWEEN ? AND ?
                ORDER BY ct.effective_date DESC, ct.created_at DESC, ct.id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new CashMovement(rs.getObject("id", UUID.class), rs.getString("direction"),
                rs.getString("amount_minor"), rs.getDate("effective_date").toLocalDate(), rs.getString("origin"),
                rs.getString("description"), rs.getObject("category_id", UUID.class), rs.getString("category_name"),
                rs.getObject("financial_account_id", UUID.class), rs.getString("account_name")),
                tenantId, from, to, size, page * size);
        return new MovementPage(items, total, page, size);
    }

    private static Object[] titleBalanceArguments(UUID tenantId, LocalDate asOf, Object... tail) {
        var arguments = new Object[4 + tail.length];
        arguments[0] = tenantId;
        arguments[1] = asOf;
        arguments[2] = asOf;
        arguments[3] = tenantId;
        System.arraycopy(tail, 0, arguments, 4, tail.length);
        return arguments;
    }

    private static final String TITLE_BALANCE_CTE = """
            WITH reversed AS (
                SELECT tenant_id, financial_settlement_id, sum(amount_minor) AS amount_minor
                FROM financial_settlement_reversal
                WHERE tenant_id = ? AND effective_date <= ?
                GROUP BY tenant_id, financial_settlement_id
            ), title_balances AS (
                SELECT t.id, t.kind, t.description, t.due_date, c.name AS contact_name,
                       t.original_amount_minor - COALESCE(sum(s.amount_minor - COALESCE(r.amount_minor, 0)), 0) AS remaining_minor
                FROM financial_title t
                JOIN contact c ON c.tenant_id = t.tenant_id AND c.id = t.contact_id
                LEFT JOIN financial_settlement s ON s.tenant_id = t.tenant_id AND s.financial_title_id = t.id AND s.effective_date <= ?
                LEFT JOIN reversed r ON r.tenant_id = s.tenant_id AND r.financial_settlement_id = s.id
                WHERE t.tenant_id = ? AND t.status = 'OPEN'
                GROUP BY t.id, t.kind, t.description, t.due_date, c.name, t.original_amount_minor
            )
            """;
}
