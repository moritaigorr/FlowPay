package com.flowpay.reporting.application;

import com.flowpay.identity.api.TenantAccess;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {
    private static final int MAX_PERIOD_DAYS = 366;
    private final JdbcTemplate jdbc;
    private final TenantAccess tenantAccess;

    public DashboardService(JdbcTemplate jdbc, TenantAccess tenantAccess) {
        this.jdbc = jdbc;
        this.tenantAccess = tenantAccess;
    }

    @Transactional(readOnly = true)
    public DashboardView get(String email, LocalDate from, LocalDate to, String timezone) {
        var zone = parseZone(timezone);
        var today = LocalDate.now(zone);
        var start = from == null ? today.withDayOfMonth(1) : from;
        var end = to == null ? today.withDayOfMonth(today.lengthOfMonth()) : to;
        validatePeriod(start, end);
        var tenant = tenantAccess.requireSingleTenant(email);

        var cash = jdbc.queryForObject("""
                SELECT COALESCE(sum(amount_minor) FILTER (WHERE origin <> 'OPENING_BALANCE' AND direction = 'INFLOW' AND effective_date BETWEEN ? AND ?), 0)::text AS inflows,
                       COALESCE(sum(amount_minor) FILTER (WHERE origin <> 'OPENING_BALANCE' AND direction = 'OUTFLOW' AND effective_date BETWEEN ? AND ?), 0)::text AS outflows,
                       COALESCE(sum(CASE direction WHEN 'INFLOW' THEN amount_minor ELSE -amount_minor END)
                                FILTER (WHERE effective_date <= ?), 0)::text AS balance
                FROM cash_transaction
                WHERE tenant_id = ?
                """, (rs, row) -> new CashTotals(rs.getString("balance"), rs.getString("inflows"), rs.getString("outflows")),
                start, end, start, end, end, tenant.id());

        var forecast = jdbc.queryForObject("""
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
                SELECT COALESCE(sum(remaining_minor) FILTER (WHERE kind = 'RECEIVABLE' AND due_date BETWEEN ? AND ? AND remaining_minor > 0), 0)::text AS receivables_due,
                       COALESCE(sum(remaining_minor) FILTER (WHERE kind = 'PAYABLE' AND due_date BETWEEN ? AND ? AND remaining_minor > 0), 0)::text AS payables_due,
                       COALESCE(sum(remaining_minor) FILTER (WHERE kind = 'RECEIVABLE' AND due_date < ? AND remaining_minor > 0), 0)::text AS overdue_receivables,
                       COALESCE(sum(remaining_minor) FILTER (WHERE kind = 'PAYABLE' AND due_date < ? AND remaining_minor > 0), 0)::text AS overdue_payables
                FROM title_balances
                """, (rs, row) -> new ForecastTotals(rs.getString("receivables_due"), rs.getString("payables_due"),
                        rs.getString("overdue_receivables"), rs.getString("overdue_payables")),
                tenant.id(), end, end, tenant.id(), start, end, start, end, end, end);

        var upcoming = jdbc.query("""
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
                SELECT id, kind, description, contact_name, due_date, remaining_minor::text AS remaining_minor
                FROM title_balances
                WHERE due_date > ? AND due_date <= ? AND remaining_minor > 0
                ORDER BY due_date, lower(description), id
                LIMIT 5
                """, (rs, row) -> new UpcomingDue(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("description"),
                        rs.getString("contact_name"), rs.getDate("due_date").toLocalDate(), rs.getString("remaining_minor")),
                tenant.id(), end, end, tenant.id(), end, end.plusDays(30));

        var recent = jdbc.query("""
                SELECT ct.id, ct.direction, ct.amount_minor::text AS amount_minor, ct.effective_date,
                       ct.origin, ct.description, a.name AS account_name
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                WHERE ct.tenant_id = ? AND ct.effective_date BETWEEN ? AND ?
                ORDER BY ct.effective_date DESC, ct.created_at DESC, ct.id DESC
                LIMIT 5
                """, (rs, row) -> new RecentMovement(rs.getObject("id", UUID.class), rs.getString("direction"),
                        rs.getString("amount_minor"), rs.getDate("effective_date").toLocalDate(), rs.getString("origin"),
                        rs.getString("description"), rs.getString("account_name")), tenant.id(), start, end);

        return new DashboardView(start, end, zone.getId(), end, cash.balanceMinor(), cash.inflowsMinor(), cash.outflowsMinor(),
                forecast.receivablesDueMinor(), forecast.payablesDueMinor(), forecast.overdueReceivablesMinor(),
                forecast.overduePayablesMinor(), upcoming, recent);
    }

    private static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um fuso horário IANA válido.");
        }
    }

    private static void validatePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O período deve ser válido e ter no máximo 366 dias.");
        }
    }

    public record DashboardView(LocalDate from, LocalDate to, String timezone, LocalDate asOf,
                                String controlledBalanceMinor, String periodInflowsMinor, String periodOutflowsMinor,
                                String receivablesDueMinor, String payablesDueMinor,
                                String overdueReceivablesMinor, String overduePayablesMinor,
                                List<UpcomingDue> upcomingDues, List<RecentMovement> recentMovements) {}
    public record UpcomingDue(UUID id, String kind, String description, String contactName,
                              LocalDate dueDate, String remainingMinor) {}
    public record RecentMovement(UUID id, String direction, String amountMinor, LocalDate effectiveDate,
                                 String origin, String description, String accountName) {}
    private record CashTotals(String balanceMinor, String inflowsMinor, String outflowsMinor) {}
    private record ForecastTotals(String receivablesDueMinor, String payablesDueMinor,
                                  String overdueReceivablesMinor, String overduePayablesMinor) {}
}
