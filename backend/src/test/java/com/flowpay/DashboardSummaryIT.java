package com.flowpay;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.assertThat;

class DashboardSummaryIT extends PaymentTestSupport {
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");
    private static final String MAX_AMOUNT = "99999999999999999.99";

    private UUID payment(Session session, String amount, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment(id,company_id,customer_id,amount,currency,status,simulation_scenario,created_at,updated_at)
                VALUES (?,?,?,?,'BRL',?,'APPROVE',?,?)
                """, id, session.company(), session.customer(), new BigDecimal(amount), status,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        return id;
    }

    private JsonNode summary(Session session, Instant from, Instant to) throws Exception {
        String path = "/api/v1/dashboard/summary?from=" + encode(from.toString()) + "&to=" + encode(to.toString());
        var response = request("GET", path, session.cookie(), null, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        return json(response);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    void computesCreationCohortMetricsFromCurrentStatuses() throws Exception {
        var session = login();
        payment(session, "150.00", "APPROVED", FROM.plusSeconds(1));
        payment(session, "20.00", "REJECTED", FROM.plusSeconds(2));
        payment(session, "30.00", "PENDING", FROM.plusSeconds(3));
        payment(session, "40.00", "CANCELLED", FROM.plusSeconds(4));

        var result = summary(session, FROM, TO);
        assertThat(result.properties()).hasSize(10);
        assertThat(result.get("from").asString()).isEqualTo(FROM.toString());
        assertThat(result.get("to").asString()).isEqualTo(TO.toString());
        assertThat(result.get("currency").asString()).isEqualTo("BRL");
        assertThat(result.get("approvedVolume").asString()).isEqualTo("150.00");
        assertThat(result.get("pendingCount").asLong()).isEqualTo(1);
        assertThat(result.get("pendingAmount").asString()).isEqualTo("30.00");
        assertThat(result.get("approvedCount").asLong()).isEqualTo(1);
        assertThat(result.get("rejectedCount").asLong()).isEqualTo(1);
        assertThat(result.get("totalPayments").asLong()).isEqualTo(4);
        assertThat(result.get("approvalRate").asString()).isEqualTo("0.5000");
    }

    @Test
    void returnsNullWhenApprovalDenominatorIsZeroAndExcludesAnotherCompany() throws Exception {
        var own = login();
        var foreign = login();
        payment(own, "30.00", "PENDING", FROM.plusSeconds(1));
        payment(foreign, "150.00", "APPROVED", FROM.plusSeconds(1));
        payment(foreign, "20.00", "REJECTED", FROM.plusSeconds(2));

        var result = summary(own, FROM, TO);
        assertThat(result.get("approvedCount").asLong()).isZero();
        assertThat(result.get("rejectedCount").asLong()).isZero();
        assertThat(result.get("totalPayments").asLong()).isEqualTo(1);
        assertThat(result.get("approvalRate").isNull()).isTrue();
    }

    @Test
    void usesHalfOpenUtcCreationWindowAndReflectsLaterProcessing() throws Exception {
        var session = login();
        payment(session, "5.00", "APPROVED", FROM);
        payment(session, "6.00", "PENDING", TO.minusNanos(1_000));
        payment(session, "99.00", "APPROVED", TO);
        payment(session, "88.00", "REJECTED", FROM.minusNanos(1_000));
        UUID transitionId = payment(session, "12.30", "PENDING", FROM.plusSeconds(5));

        var before = summary(session, FROM, TO);
        assertThat(before.get("totalPayments").asLong()).isEqualTo(3);
        assertThat(before.get("approvedVolume").asString()).isEqualTo("5.00");
        assertThat(before.get("pendingAmount").asString()).isEqualTo("18.30");

        var transitioned = request("POST", "/api/v1/payments/" + transitionId + "/process", session.cookie(), session.csrf(), null, null);
        assertThat(transitioned.statusCode()).as(transitioned.body()).isEqualTo(200);

        var after = summary(session, FROM, TO);
        assertThat(after.get("approvedCount").asLong()).isEqualTo(2);
        assertThat(after.get("pendingCount").asLong()).isEqualTo(1);
        assertThat(after.get("totalPayments").asLong()).isEqualTo(3);
        assertThat(after.get("approvedVolume").asString()).isEqualTo("17.30");
    }

    @Test
    void aggregateSumsCanExceedIndividualPaymentPrecision() throws Exception {
        var session = login();
        payment(session, MAX_AMOUNT, "APPROVED", FROM.plusSeconds(1));
        payment(session, MAX_AMOUNT, "APPROVED", FROM.plusSeconds(2));

        var result = summary(session, FROM, TO);
        assertThat(result.get("approvedVolume").isString()).isTrue();
        assertThat(result.get("approvedVolume").asString()).isEqualTo("199999999999999999.98");
        assertThat(result.get("approvalRate").asString()).isEqualTo("1.0000");
    }

    @Test
    void requiresAscendingRange() throws Exception {
        var session = login();
        var response = request("GET", "/api/v1/dashboard/summary?from=" + encode(TO.toString()) + "&to=" + encode(FROM.toString()),
                session.cookie(), null, null, null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("VALIDATION_FAILED", "from");
    }
}
