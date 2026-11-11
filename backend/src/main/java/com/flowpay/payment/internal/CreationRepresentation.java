package com.flowpay.payment.internal;

import com.flowpay.payment.NewPayment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class CreationRepresentation {
    // Isolated from application mapper customization: tree fields retain this explicit contract order.
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CreationRepresentation() {}

    private static ObjectNode fields(NewPayment command) {
        return JSON.createObjectNode()
                .put("customerId", command.customerId().toString())
                .put("amount", command.amount().toPlainString())
                .put("currency", command.currency())
                .put("description", command.description())
                .put("simulationScenario", command.simulationScenario().name());
    }

    static String hash(NewPayment command) {
        byte[] canonical = JSON.writeValueAsString(fields(command)).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }

    static String response(UUID paymentId, NewPayment command, Instant now) {
        var response = fields(command);
        response.put("id", paymentId.toString()).put("status", "PENDING")
                .put("createdAt", now.toString()).put("updatedAt", now.toString());
        return JSON.writeValueAsString(response);
    }
}
