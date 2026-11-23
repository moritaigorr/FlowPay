package com.flowpay.reporting.api;

import com.flowpay.reporting.application.MonthlyReportService;
import com.flowpay.reporting.application.MonthlyReportService.MonthlyReportView;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports/monthly")
public class MonthlyReportController {
    private final MonthlyReportService reports;

    public MonthlyReportController(MonthlyReportService reports) { this.reports = reports; }

    @GetMapping
    MonthlyReportView get(@AuthenticationPrincipal UserDetails user,
                          @RequestParam String month,
                          @RequestParam String timezone,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        final YearMonth requestedMonth;
        try {
            requestedMonth = YearMonth.parse(month);
        } catch (DateTimeParseException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o mÃªs no formato AAAA-MM.");
        }
        return reports.get(user.getUsername(), requestedMonth, timezone, page, size);
    }
}
