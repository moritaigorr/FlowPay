package com.flowpay.statements.application;

import com.flowpay.finance.api.FinanceReportingReader;
import com.flowpay.identity.api.TenantAccess;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StatementReconciliationSummaryService {
    private final TenantAccess tenants;
    private final FinanceReportingReader finance;
    private final JdbcTemplate jdbc;

    public StatementReconciliationSummaryService(TenantAccess tenants, FinanceReportingReader finance, JdbcTemplate jdbc) {
        this.tenants = tenants; this.finance = finance; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary get(String email, UUID importId, String timezone) {
        var zone = parseZone(timezone);
        var tenant = tenants.requireSingleTenant(email);
        var source = jdbc.query("""
                SELECT si.financial_account_id,
                       min(sl.effective_date) FILTER (WHERE sl.row_status='ACCEPTED') AS period_from,
                       max(sl.effective_date) FILTER (WHERE sl.row_status='ACCEPTED') AS period_to,
                       count(*) FILTER (WHERE sl.row_status='ACCEPTED') AS accepted_count,
                       count(*) FILTER (WHERE sl.row_status='REJECTED') AS rejected_count,
                       count(*) FILTER (WHERE sl.row_status='DUPLICATE') AS duplicate_count,
                       COALESCE(sum(sl.amount_minor) FILTER (WHERE sl.row_status='ACCEPTED' AND sl.direction='INFLOW'), 0)::text AS statement_inflows,
                       COALESCE(sum(sl.amount_minor) FILTER (WHERE sl.row_status='ACCEPTED' AND sl.direction='OUTFLOW'), 0)::text AS statement_outflows,
                       count(*) FILTER (WHERE sl.row_status='ACCEPTED' AND EXISTS (
                           SELECT 1 FROM reconciliation_match rm WHERE rm.tenant_id=sl.tenant_id
                             AND rm.statement_line_id=sl.id AND rm.reversed_at IS NULL)) AS matched_count
                FROM statement_import si
                LEFT JOIN statement_line sl ON sl.tenant_id=si.tenant_id AND sl.statement_import_id=si.id
                WHERE si.tenant_id=? AND si.id=?
                GROUP BY si.financial_account_id
                """, (rs, row) -> {
            var from = rs.getDate("period_from");
            var to = rs.getDate("period_to");
            return new ImportActivity(rs.getObject("financial_account_id", UUID.class),
                    from == null ? null : from.toLocalDate(), to == null ? null : to.toLocalDate(),
                    rs.getLong("accepted_count"), rs.getLong("rejected_count"), rs.getLong("duplicate_count"),
                    rs.getString("statement_inflows"), rs.getString("statement_outflows"), rs.getLong("matched_count"));
        }, tenant.id(), importId).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Importação não encontrada."));

        if (source.periodFrom() == null || source.periodTo() == null) {
            return new Summary(importId, source.accountId(), null, null, zone.getId(), null, 0, source.rejectedCount(),
                    source.duplicateCount(), source.matchedCount(), source.statementInflows(), source.statementOutflows(),
                    "0", "0", null, null, null, false);
        }
        var cash = finance.accountCashSummary(tenant.id(), source.accountId(), source.periodFrom(), source.periodTo(), source.periodTo());
        var statementNet = new BigInteger(source.statementInflows()).subtract(new BigInteger(source.statementOutflows()));
        var controlledNet = new BigInteger(cash.inflowsMinor()).subtract(new BigInteger(cash.outflowsMinor()));
        var netActivityDifference = statementNet.subtract(controlledNet).toString();
        return new Summary(importId, source.accountId(), source.periodFrom(), source.periodTo(), zone.getId(), source.periodTo(),
                source.acceptedCount(), source.rejectedCount(), source.duplicateCount(), source.matchedCount(),
                source.statementInflows(), source.statementOutflows(), cash.inflowsMinor(), cash.outflowsMinor(),
                cash.controlledBalanceMinor(),
                new BigInteger(source.statementInflows()).subtract(new BigInteger(cash.inflowsMinor())).toString(),
                new BigInteger(source.statementOutflows()).subtract(new BigInteger(cash.outflowsMinor())).toString(),
                netActivityDifference, false);
    }

    private static ZoneId parseZone(String timezone) {
        try { return ZoneId.of(timezone); }
        catch (DateTimeException | NullPointerException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um fuso horário IANA válido.");
        }
    }

    private record ImportActivity(UUID accountId, LocalDate periodFrom, LocalDate periodTo, long acceptedCount,
                                  long rejectedCount, long duplicateCount, String statementInflows,
                                  String statementOutflows, long matchedCount) {}
    public record Summary(UUID importId, UUID financialAccountId, LocalDate periodFrom, LocalDate periodTo,
                          String timezone, LocalDate asOf, long acceptedCount, long rejectedCount,
                          long duplicateCount, long matchedCount, String statementInflowsMinor,
                          String statementOutflowsMinor, String controlledInflowsMinor,
                          String controlledOutflowsMinor, String controlledBalanceMinor,
                          String inflowActivityDifferenceMinor, String outflowActivityDifferenceMinor,
                          String netActivityDifferenceMinor, boolean statementBalanceAvailable) {}
}
