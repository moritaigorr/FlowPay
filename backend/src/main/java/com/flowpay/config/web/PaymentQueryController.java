package com.flowpay.config.web;

import com.flowpay.company.ContractValidationException;
import com.flowpay.config.security.SessionIdentity;
import com.flowpay.payment.PaymentAccess;
import com.flowpay.payment.PaymentEventView;
import com.flowpay.payment.PaymentFilter;
import com.flowpay.payment.PaymentListing;
import com.flowpay.payment.PaymentStatus;
import com.flowpay.payment.PaymentView;
import com.flowpay.payment.SimulationScenario;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentQueryController {
    private static final Pattern DATE_TIME = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?(?:[Zz]|[+-][0-9]{2}:[0-9]{2})");
    private final PaymentAccess access;
    private final PaymentListing listing;

    PaymentQueryController(PaymentAccess access, PaymentListing listing) {
        this.access = access;
        this.listing = listing;
    }

    record PaymentResponse(UUID id, UUID customerId, String amount, String currency, PaymentStatus status,
            String description, SimulationScenario simulationScenario, Instant createdAt, Instant updatedAt) {
        static PaymentResponse from(PaymentView payment) {
            return new PaymentResponse(payment.id(), payment.customerId(), payment.amount().setScale(2).toPlainString(),
                    payment.currency(), payment.status(), payment.description(), payment.simulationScenario(),
                    payment.createdAt(), payment.updatedAt());
        }
    }

    record PageResponse(List<PaymentResponse> items, int page, int size, long totalElements, long totalPages) {
        PageResponse { items = List.copyOf(items); }
    }

    @GetMapping("/api/v1/payments")
    PageResponse list(@AuthenticationPrincipal SessionIdentity identity,
            @RequestParam(required = false) String page, @RequestParam(required = false) String size,
            @RequestParam(required = false) String status, @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String createdFrom, @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String q) {
        var result = listing.list(identity.companyId(), integer(page, "page", 0), integer(size, "size", 20),
                new PaymentFilter(status(status), customerId == null ? null : uuid(customerId, "customerId"),
                        instant(createdFrom, "createdFrom"), instant(createdTo, "createdTo"), q));
        return new PageResponse(result.items().stream().map(PaymentResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/api/v1/payments/{id}")
    PaymentResponse detail(@AuthenticationPrincipal SessionIdentity identity, @PathVariable String id) {
        return PaymentResponse.from(access.requireOwned(identity.companyId(), uuid(id, "id")));
    }

    @GetMapping("/api/v1/payments/{id}/events")
    List<PaymentEventView> events(@AuthenticationPrincipal SessionIdentity identity, @PathVariable String id) {
        return access.events(identity.companyId(), uuid(id, "id"));
    }

    private int integer(String value, String field, int fallback) {
        if (value == null) return fallback;
        try {
            if (!value.matches("-?[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(field, "An integer value is required.");
        }
    }

    private UUID uuid(String value, String field) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException exception) {
            throw invalid(field, "A UUID value is required.");
        }
    }

    private PaymentStatus status(String value) {
        if (value == null) return null;
        try {
            return PaymentStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("status", "A documented payment status is required.");
        }
    }

    private Instant instant(String value, String field) {
        if (value == null) return null;
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
