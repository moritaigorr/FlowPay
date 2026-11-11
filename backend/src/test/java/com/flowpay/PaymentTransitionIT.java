package com.flowpay;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentTransitionIT extends PaymentTestSupport {
    private UUID createPayment(Session session, String key, String scenario) throws Exception {
        String body = body(session).replace("APPROVE", scenario);
        var response = create(session, key, body);
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json(response).get("id").asString());
    }

    private java.net.http.HttpResponse<String> action(Session session, UUID id, String action) throws Exception {
        return request("POST", "/api/v1/payments/" + id + "/" + action,
                session.cookie(), session.csrf(), null, null);
    }

    @Test
    void processMapsScenarioToFinalStatusAndAppendsOneEvent() throws Exception {
        var session = login();
        for (String scenario : new String[]{"APPROVE", "DECLINE"}) {
            UUID id = createPayment(session, "process-" + scenario, scenario);
            var response = action(session, id, "process");
            assertThat(response.statusCode()).isEqualTo(200);
            String expectedStatus = scenario.equals("APPROVE") ? "APPROVED" : "REJECTED";
            assertThat(json(response).get("status").asString()).isEqualTo(expectedStatus);
            var events = json(request("GET", "/api/v1/payments/" + id + "/events",
                    session.cookie(), null, null, null));
            assertThat(events.size()).isEqualTo(2);
            assertThat(events.get(1).get("previousStatus").asString()).isEqualTo("PENDING");
            assertThat(events.get(1).get("newStatus").asString()).isEqualTo(expectedStatus);
            if (scenario.equals("DECLINE")) {
                assertThat(events.get(1).get("reasonCode").asString()).isEqualTo("SIMULATED_DECLINE");
            } else {
                assertThat(events.get(1).get("reasonCode").isNull()).isTrue();
            }
        }
    }

    @Test
    void cancelAppendsFinalEventAndEveryRepeatedFinalActionConflicts() throws Exception {
        var session = login();
        UUID id = createPayment(session, "cancel", "APPROVE");
        var canceled = action(session, id, "cancel");
        assertThat(canceled.statusCode()).isEqualTo(200);
        assertThat(json(canceled).get("status").asString()).isEqualTo("CANCELLED");
        var events = json(request("GET", "/api/v1/payments/" + id + "/events",
                session.cookie(), null, null, null));
        assertThat(events.size()).isEqualTo(2);
        assertThat(events.get(1).get("previousStatus").asString()).isEqualTo("PENDING");
        assertThat(events.get(1).get("newStatus").asString()).isEqualTo("CANCELLED");
        assertThat(events.get(1).get("reasonCode").isNull()).isTrue();
        for (String action : new String[]{"process", "cancel"}) {
            var conflict = action(session, id, action);
            assertThat(conflict.statusCode()).isEqualTo(409);
            assertThat(json(conflict).get("code").asString()).isEqualTo("PAYMENT_FINAL_STATE");
        }
        assertThat(count("payment_event", session)).isEqualTo(2);
    }

    @Test
    void foreignPaymentCannotBeProcessedOrCancelled() throws Exception {
        var owner = login();
        var stranger = login();
        UUID id = createPayment(owner, "owned-by-other", "APPROVE");
        for (String action : new String[]{"process", "cancel"}) {
            var response = action(stranger, id, action);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(json(response).get("code").asString()).isEqualTo("PAYMENT_NOT_FOUND");
        }
        assertThat(json(request("GET", "/api/v1/payments/" + id,
                owner.cookie(), null, null, null)).get("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void eventFailureRollsBackStatusAndCanBeRetried() throws Exception {
        var session = login();
        UUID id = createPayment(session, "event-rollback", "APPROVE");
        String fixture = "fail_transition_event_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE FUNCTION " + fixture + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.payment_id='" + id + "'::uuid AND NEW.sequence=2 THEN RAISE EXCEPTION 'Synthetic injected failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + fixture + " BEFORE INSERT ON payment_event FOR EACH ROW EXECUTE FUNCTION " + fixture + "()");
        try {
            var failed = action(session, id, "process");
            assertThat(failed.statusCode()).isEqualTo(500);
            assertThat(failed.body()).contains("INTERNAL_ERROR").doesNotContain("Synthetic injected failure");
            assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE id=?", String.class, id)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE payment_id=?", Long.class, id)).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER " + fixture + " ON payment_event");
            jdbc.execute("DROP FUNCTION " + fixture + "()");
        }
        assertThat(action(session, id, "process").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE id=?", String.class, id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE payment_id=?", Long.class, id)).isEqualTo(2);
    }
}
