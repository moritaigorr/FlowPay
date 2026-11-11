package com.flowpay.config.web;

import com.flowpay.company.ContractValidationException;
import com.flowpay.config.security.SessionIdentity;
import com.flowpay.payment.PaymentOperations;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentTransitionController {
    private static final Pattern UUID_FORMAT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private final PaymentOperations payments;

    PaymentTransitionController(PaymentOperations payments) {
        this.payments = payments;
    }

    @PostMapping("/api/v1/payments/{id}/process")
    PaymentQueryController.PaymentResponse process(@AuthenticationPrincipal SessionIdentity identity, @PathVariable String id) {
        return PaymentQueryController.PaymentResponse.from(
                payments.process(identity.companyId(), identity.userId(), uuid(id)));
    }

    @PostMapping("/api/v1/payments/{id}/cancel")
    PaymentQueryController.PaymentResponse cancel(@AuthenticationPrincipal SessionIdentity identity, @PathVariable String id) {
        return PaymentQueryController.PaymentResponse.from(
                payments.cancel(identity.companyId(), identity.userId(), uuid(id)));
    }

    private UUID uuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!UUID_FORMAT.matcher(value).matches() || !parsed.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new ContractValidationException("id", "INVALID_FORMAT", "A UUID value is required.");
        }
    }
}
