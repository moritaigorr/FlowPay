package com.flowpay.finance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public read contract used by the reporting module without exposing finance persistence details. */
public interface FinanceReportingReader {
    CashSummary cashSummary(UUID tenantId, LocalDate from, LocalDate to, LocalDate asOf);

    CashSummary accountCashSummary(UUID tenantId, UUID accountId, LocalDate from, LocalDate to, LocalDate asOf);

    TitleSummary titleSummary(UUID tenantId, LocalDate dueFrom, LocalDate dueTo, LocalDate asOf);

    List<CategoryFlow> cashFlowsByCategory(UUID tenantId, LocalDate from, LocalDate to);

    List<OutstandingTitle> upcomingTitles(UUID tenantId, LocalDate after, LocalDate through, LocalDate asOf, int limit);

    MovementPage movements(UUID tenantId, LocalDate from, LocalDate to, int page, int size);

    record CashSummary(String controlledBalanceMinor, String inflowsMinor, String outflowsMinor) {}

    record TitleSummary(String receivablesDueMinor, long receivablesDueCount,
                        String payablesDueMinor, long payablesDueCount,
                        String overdueReceivablesMinor, long overdueReceivablesCount,
                        String overduePayablesMinor, long overduePayablesCount) {}

    record CategoryFlow(UUID categoryId, String categoryName, String direction,
                        String amountMinor, long movementCount) {}

    record OutstandingTitle(UUID id, String kind, String description, String contactName,
                            LocalDate dueDate, String remainingMinor) {}

    record CashMovement(UUID id, String direction, String amountMinor, LocalDate effectiveDate,
                       String origin, String description, UUID categoryId, String categoryName,
                       UUID accountId, String accountName) {}

    record MovementPage(List<CashMovement> items, long total, int page, int size) {}
}
