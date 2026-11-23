package com.flowpay.reporting.application;

import com.flowpay.identity.api.TenantAccess;
import com.flowpay.finance.api.FinanceReportingReader;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {
    private static final int MAX_PERIOD_DAYS = 366;
    private final TenantAccess tenantAccess;
    private final FinanceReportingReader finance;

    public DashboardService(TenantAccess tenantAccess, FinanceReportingReader finance) {
        this.tenantAccess = tenantAccess;
        this.finance = finance;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardView get(String email, LocalDate from, LocalDate to, String timezone) {
        var zone = parseZone(timezone);
        var today = LocalDate.now(zone);
        var start = from == null ? today.withDayOfMonth(1) : from;
        var end = to == null ? today.withDayOfMonth(today.lengthOfMonth()) : to;
        validatePeriod(start, end);
        var tenant = tenantAccess.requireSingleTenant(email);

        var cash = finance.cashSummary(tenant.id(), start, end, end);
        var forecast = finance.titleSummary(tenant.id(), start, end, end);
        var upcoming = finance.upcomingTitles(tenant.id(), end, end.plusDays(30), end, 5);
        var recent = finance.movements(tenant.id(), start, end, 0, 5).items();

        return new DashboardView(start, end, zone.getId(), end, cash.controlledBalanceMinor(), cash.inflowsMinor(), cash.outflowsMinor(),
                forecast.receivablesDueMinor(), forecast.payablesDueMinor(), forecast.overdueReceivablesMinor(),
                forecast.overduePayablesMinor(), upcoming.stream().map(item -> new UpcomingDue(item.id(), item.kind(), item.description(),
                item.contactName(), item.dueDate(), item.remainingMinor())).toList(), recent.stream().map(item -> new RecentMovement(
                item.id(), item.direction(), item.amountMinor(), item.effectiveDate(), item.origin(), item.description(), item.accountName())).toList());
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
}
