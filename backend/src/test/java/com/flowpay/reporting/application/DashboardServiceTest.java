package com.flowpay.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DashboardServiceTest {
    private final DashboardService service = new DashboardService(null, email -> {
        throw new AssertionError("Tenant lookup must not run for invalid dashboard parameters.");
    });

    @Test
    void rejectsReversedPeriod() {
        assertBadRequest(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-01"), "America/Sao_Paulo");
    }

    @Test
    void rejectsPeriodLongerThan366InclusiveDays() {
        assertBadRequest(LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-02"), "America/Sao_Paulo");
    }

    @Test
    void rejectsUnknownTimezone() {
        assertThatThrownBy(() -> service.get("user@example.test", LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-30"), "Mars/Olympus"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void assertBadRequest(LocalDate from, LocalDate to, String timezone) {
        assertThatThrownBy(() -> service.get("user@example.test", from, to, timezone))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
