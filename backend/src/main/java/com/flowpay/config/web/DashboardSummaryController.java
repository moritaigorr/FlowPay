package com.flowpay.config.web;

import com.flowpay.company.ContractValidationException;
import com.flowpay.config.security.SessionIdentity;
import com.flowpay.payment.PaymentSummary;
import com.flowpay.payment.PaymentSummaryReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DashboardSummaryController {
    private static final Pattern DATE_TIME = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?(?:[Zz]|[+-][0-9]{2}:[0-9]{2})");
    private final PaymentSummaryReader summaries;

    DashboardSummaryController(PaymentSummaryReader summaries) {
        this.summaries = summaries;
    }

    record SummaryResponse(Instant from, Instant to, String currency, String approvedVolume, long pendingCount,
            String pendingAmount, long approvedCount, long rejectedCount, long totalPayments, String approvalRate) {
        static SummaryResponse from(PaymentSummary summary) {
            return new SummaryResponse(summary.from(), summary.to(), summary.currency(), money(summary.approvedVolume()),
                    summary.pendingCount(), money(summary.pendingAmount()), summary.approvedCount(), summary.rejectedCount(),
                    summary.totalPayments(), summary.approvalRate());
        }

        private static String money(BigDecimal value) {
            return value.setScale(2).toPlainString();
        }
    }

    @GetMapping("/api/v1/dashboard/summary")
    SummaryResponse summary(@AuthenticationPrincipal SessionIdentity identity,
            @RequestParam String from, @RequestParam String to) {
        return SummaryResponse.from(summaries.summarize(identity.companyId(), instant(from, "from"), instant(to, "to")));
    }

    private Instant instant(String value, String field) {
        try {
            if (!DATE_TIME.matcher(value).matches()) throw invalid(field, "An offset date-time is required.");
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            throw invalid(field, "An offset date-time is required.");
        }
    }

    private ContractValidationException invalid(String field, String message) {
        return new ContractValidationException(field, "INVALID_FORMAT", message);
    }
}
