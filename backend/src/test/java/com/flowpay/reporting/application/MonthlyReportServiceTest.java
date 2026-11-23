package com.flowpay.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class MonthlyReportServiceTest {
    private final MonthlyReportService service = new MonthlyReportService(email -> {
        throw new AssertionError("Tenant lookup must not run for invalid report parameters.");
    }, null);

    @Test
    void rejectsOutOfRangeYears() {
        assertBadRequest(YearMonth.of(1899, 12), "America/Sao_Paulo", 0, 20);
        assertBadRequest(YearMonth.of(10000, 1), "America/Sao_Paulo", 0, 20);
    }

    @Test
    void rejectsInvalidPagination() {
        assertBadRequest(YearMonth.of(2026, 11), "America/Sao_Paulo", -1, 20);
        assertBadRequest(YearMonth.of(2026, 11), "America/Sao_Paulo", 0, 101);
    }

    @Test
    void rejectsUnknownTimezone() {
        assertThatThrownBy(() -> service.get("user@example.test", YearMonth.of(2026, 11), "Mars/Olympus", 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void assertBadRequest(YearMonth month, String timezone, int page, int size) {
        assertThatThrownBy(() -> service.get("user@example.test", month, timezone, page, size))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
