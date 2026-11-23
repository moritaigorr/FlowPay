package com.flowpay.reporting.application;

import com.flowpay.finance.api.FinanceReportingReader;
import com.flowpay.identity.api.TenantAccess;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonthlyReportService {
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 9999;
    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_PAGE_SIZE = 100;
    private final TenantAccess tenantAccess;
    private final FinanceReportingReader finance;

    public MonthlyReportService(TenantAccess tenantAccess, FinanceReportingReader finance) {
        this.tenantAccess = tenantAccess;
        this.finance = finance;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthlyReportView get(String email, YearMonth month, String timezone, int page, int size) {
        var zone = parseZone(timezone);
        if (month.getYear() < MIN_YEAR || month.getYear() > MAX_YEAR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ano deve estar entre 1900 e 9999.");
        }
        validatePage(page, size);
        var from = month.atDay(1);
        var to = month.atEndOfMonth();
        var tenant = tenantAccess.requireSingleTenant(email);

        var cash = finance.cashSummary(tenant.id(), from, to, to);
        var titles = finance.titleSummary(tenant.id(), from, to, to);
        var categories = finance.cashFlowsByCategory(tenant.id(), from, to).stream()
                .map(item -> new CategoryTotal(item.categoryId(), item.categoryName(), item.direction(), item.amountMinor(), item.movementCount()))
                .toList();
        var movements = finance.movements(tenant.id(), from, to, page, size);

        return new MonthlyReportView(month.toString(), from, to, zone.getId(), to, cash.inflowsMinor(), cash.outflowsMinor(),
                titles.receivablesDueMinor(), titles.receivablesDueCount(), titles.payablesDueMinor(), titles.payablesDueCount(),
                titles.overdueReceivablesMinor(), titles.overdueReceivablesCount(), titles.overduePayablesMinor(), titles.overduePayablesCount(),
                categories, movements.items().stream().map(item -> new Movement(item.id(), item.direction(), item.amountMinor(),
                item.effectiveDate(), item.origin(), item.description(), item.categoryId(), item.categoryName(), item.accountId(), item.accountName())).toList(),
                movements.total(), movements.page(), movements.size());
    }

    private static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um fuso horÃ¡rio IANA vÃ¡lido.");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PÃ¡gina ou tamanho fora dos limites permitidos.");
        }
    }

    public record MonthlyReportView(String month, LocalDate from, LocalDate to, String timezone, LocalDate asOf,
                                    String periodInflowsMinor, String periodOutflowsMinor,
                                    String receivablesDueMinor, long receivablesDueCount,
                                    String payablesDueMinor, long payablesDueCount,
                                    String overdueReceivablesMinor, long overdueReceivablesCount,
                                    String overduePayablesMinor, long overduePayablesCount,
                                    List<CategoryTotal> categories, List<Movement> movements,
                                    long totalMovements, int page, int size) {}
    public record CategoryTotal(UUID categoryId, String categoryName, String direction, String amountMinor, long movementCount) {}
    public record Movement(UUID id, String direction, String amountMinor, LocalDate effectiveDate,
                           String origin, String description, UUID categoryId, String categoryName,
                           UUID accountId, String accountName) {}
}
