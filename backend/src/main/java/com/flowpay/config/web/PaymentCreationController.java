package com.flowpay.config.web;

import com.flowpay.company.ContractValidationException;
import com.flowpay.config.security.SessionIdentity;
import com.flowpay.payment.NewPayment;
import com.flowpay.payment.PaymentOperations;
import com.flowpay.payment.SimulationScenario;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
class PaymentCreationController {
    private static final Pattern MONEY = Pattern.compile("(0|[1-9][0-9]{0,16})(\\.[0-9]{1,2})?");
    private static final Pattern UUID_FORMAT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private final PaymentOperations payments;

    PaymentCreationController(PaymentOperations payments) { this.payments = payments; }

    record CreatePaymentRequest(JsonNode customerId, JsonNode amount, JsonNode currency,
            JsonNode description, JsonNode simulationScenario) {}

    @PostMapping("/api/v1/payments")
    ResponseEntity<String> create(@AuthenticationPrincipal SessionIdentity identity,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestBody CreatePaymentRequest body) {
        String customer = requiredText(body.customerId(), "customerId");
        if (!UUID_FORMAT.matcher(customer).matches()) throw invalid("customerId", "A UUID string is required.");
        String amount = requiredText(body.amount(), "amount");
        // BigDecimal accepts exponent/sign syntax: validate the wire grammar before conversion.
        if (amount.length() > 20 || !MONEY.matcher(amount).matches()) {
            throw invalid("amount", "A decimal string with at most two decimal places is required.");
        }
        String currency = requiredText(body.currency(), "currency");
        String scenario = requiredText(body.simulationScenario(), "simulationScenario");
        SimulationScenario simulation;
        try { simulation = SimulationScenario.valueOf(scenario); }
        catch (IllegalArgumentException exception) { throw invalid("simulationScenario", "A supported simulation scenario is required."); }
        var created = payments.create(identity.companyId(), identity.userId(), key,
                new NewPayment(UUID.fromString(customer), new BigDecimal(amount), currency,
                        text(body.description(), "description"), simulation));
        // Write the exact committed JSON, without serializing the string or loading current payment state.
        return ResponseEntity.status(created.httpStatus()).contentType(MediaType.APPLICATION_JSON)
                .header("Location", created.location()).header("Idempotency-Replayed", Boolean.toString(created.replayed()))
                .body(created.responseJson());
    }

    private String text(JsonNode value, String field) {
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw invalid(field, "A string value is required.");
        return value.asString();
    }

    private String requiredText(JsonNode value, String field) {
        String text = text(value, field);
        if (text == null) throw new ContractValidationException(field, "REQUIRED", "This field is required.");
        return text;
    }

    private ContractValidationException invalid(String field, String message) {
        return new ContractValidationException(field, "INVALID_FORMAT", message);
    }
}
