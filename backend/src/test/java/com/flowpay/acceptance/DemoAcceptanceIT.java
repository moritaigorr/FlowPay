package com.flowpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.flowpay.config.demo.DemoCleanupScheduler;

/** Black-box demo checks using HTTP for every asserted outcome. */
class DemoAcceptanceIT extends AcceptanceHttpSupport {
    @Autowired DemoCleanupScheduler cleanup;
    private final List<UUID> created = new ArrayList<>();

    record Demo(Browser browser, UUID company, UUID operator, UUID customer) {}

    @AfterEach
    void removeDemos() {
        created.forEach(id -> jdbc.update("UPDATE company SET expires_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?", id));
        cleanup.cleanExpiredDemos();
        created.clear();
    }

    @Test
    void entryIsCsrfProtectedIsolatedReusableAndExpiresExplicitly() throws Exception {
        Browser anonymous = anonymous();
        long before = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class);
        var csrfRejected = request("POST", "/api/v1/demo/sessions", anonymous.cookie(), null, null, null);
        assertThat(csrfRejected.statusCode()).isEqualTo(403);
        assertThat(json(csrfRejected).path("code").asString()).isEqualTo("CSRF_INVALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class)).isEqualTo(before);

        Demo first = enter(anonymous);
        created.add(first.company());
        Demo second = enter(anonymous());
        created.add(second.company());
        assertThat(first.company()).isNotEqualTo(second.company());
        assertThat(first.operator()).isNotEqualTo(second.operator());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE company_id=?", Long.class, first.company())).isEqualTo(4);

        Browser freshCsrf = csrf(first.browser().cookie());
        var reused = request("POST", "/api/v1/demo/sessions", freshCsrf.cookie(), freshCsrf.csrf(), null, null);
        assertThat(reused.statusCode()).isEqualTo(200);
        assertThat(json(reused).path("company").path("id").asString()).isEqualTo(first.company().toString());

        var passwordLoginCsrf = anonymous();
        var passwordLogin = request("POST", "/api/v1/auth/login", passwordLoginCsrf.cookie(), passwordLoginCsrf.csrf(), null,
                mapper.writeValueAsString(Map.of("email", json(reused).path("user").path("email").asString(), "password", "anything")));
        assertThat(passwordLogin.statusCode()).isEqualTo(401);
        assertThat(json(passwordLogin).path("code").asString()).isEqualTo("INVALID_CREDENTIALS");

        jdbc.update("UPDATE company SET expires_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?", first.company());
        var expired = request("GET", "/api/v1/auth/me", reused.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0], null, null, null);
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(json(expired).path("code").asString()).isEqualTo("DEMO_EXPIRED");
    }

    @Test
    void publicWritesEnforceDemoCustomerAndPaymentLimitsAndKeepReplayAtBoundary() throws Exception {
        Demo demo = enter(anonymous());
        created.add(demo.company());
        jdbc.update("""
                INSERT INTO customer(id,company_id,name,email)
                SELECT gen_random_uuid(),?, 'Quota customer ' || n, 'quota-' || n || '-' || ? || '@example.test'
                FROM generate_series(1,96) n
                """, demo.company(), demo.company());
        String newCustomer = "{\"name\":\"Quota boundary\",\"email\":\"boundary-" + demo.company() + "@example.test\"}";
        var customerLimit = request("POST", "/api/v1/customers", demo.browser().cookie(), demo.browser().csrf(), null, newCustomer);
        assertThat(customerLimit.statusCode()).isEqualTo(409);
        assertThat(json(customerLimit).path("code").asString()).isEqualTo("DEMO_LIMIT_REACHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, demo.company())).isEqualTo(100);

        String key = "r13-quota-" + UUID.randomUUID();
        String body = "{\"customerId\":\"" + demo.customer() + "\",\"amount\":\"16.10\",\"currency\":\"BRL\",\"simulationScenario\":\"APPROVE\"}";
        jdbc.update("""
                INSERT INTO payment(id,company_id,customer_id,amount,currency,status,simulation_scenario)
                SELECT gen_random_uuid(),?,?,'1.00','BRL','PENDING','APPROVE' FROM generate_series(1,495)
                """, demo.company(), demo.customer());
        var fiveHundredth = request("POST", "/api/v1/payments", demo.browser().cookie(), demo.browser().csrf(), key, body);
        assertThat(fiveHundredth.statusCode()).isEqualTo(201);
        var replay = request("POST", "/api/v1/payments", demo.browser().cookie(), demo.browser().csrf(), key, body);
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("Idempotency-Replayed")).hasValue("true");
        assertThat(replay.body()).isEqualTo(fiveHundredth.body());
        var fiveHundredFirst = request("POST", "/api/v1/payments", demo.browser().cookie(), demo.browser().csrf(), "new-" + key, body);
        assertThat(fiveHundredFirst.statusCode()).isEqualTo(409);
        assertThat(json(fiveHundredFirst).path("code").asString()).isEqualTo("DEMO_LIMIT_REACHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE company_id=?", Long.class, demo.company())).isEqualTo(500);
    }

    @Test
    void activeCapacityRejectionLeavesNoPartialCompanyOrOperator() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo AND expires_at>CURRENT_TIMESTAMP", Long.class);
        String marker = "R13 capacity " + UUID.randomUUID();
        Browser anonymous = anonymous();
        try {
            jdbc.update("""
                    INSERT INTO company(id,name,demo,expires_at)
                    SELECT gen_random_uuid(),? || n,true,CURRENT_TIMESTAMP + interval '1 day' FROM generate_series(1,500) n
                    """, marker);
            var rejected = request("POST", "/api/v1/demo/sessions", anonymous.cookie(), anonymous.csrf(), null, null);
            assertThat(rejected.statusCode()).isEqualTo(503);
            assertThat(rejected.headers().firstValue("Retry-After")).hasValue("60");
            assertThat(json(rejected).path("code").asString()).isEqualTo("DEMO_CAPACITY_REACHED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE demo AND expires_at>CURRENT_TIMESTAMP", Long.class)).isEqualTo(before + 500);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user u JOIN company c ON c.id=u.company_id WHERE c.demo AND c.name LIKE ?", Long.class, marker + "%")).isZero();
        } finally {
            jdbc.update("DELETE FROM company WHERE name LIKE ?", marker + "%");
        }
    }

    @Test
    void provisioningFailureAfterSeedBeginsRollsBackThroughThePublicEndpoint() throws Exception {
        Browser anonymous = anonymous();
        long companiesBefore = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class);
        long operatorsBefore = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE demo", Long.class);
        long demoCustomersBefore = jdbc.queryForObject("SELECT count(*) FROM customer c JOIN company co ON co.id=c.company_id WHERE co.demo", Long.class);
        long demoPaymentsBefore = jdbc.queryForObject("SELECT count(*) FROM payment p JOIN company co ON co.id=p.company_id WHERE co.demo", Long.class);
        try {
            jdbc.execute("""
                    CREATE FUNCTION r13_reject_demo_seed() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF (SELECT demo FROM company WHERE id=NEW.company_id) THEN
                            RAISE EXCEPTION 'injected R13 demo seed failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$
                    """);
            jdbc.execute("CREATE TRIGGER r13_fail_demo_payment BEFORE INSERT ON payment FOR EACH ROW EXECUTE FUNCTION r13_reject_demo_seed()");
            var failed = request("POST", "/api/v1/demo/sessions", anonymous.cookie(), anonymous.csrf(), null, null);
            assertThat(failed.statusCode()).isEqualTo(500);
            assertThat(json(failed).path("code").asString()).isEqualTo("INTERNAL_ERROR");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class)).isEqualTo(companiesBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE demo", Long.class)).isEqualTo(operatorsBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer c JOIN company co ON co.id=c.company_id WHERE co.demo", Long.class)).isEqualTo(demoCustomersBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payment p JOIN company co ON co.id=p.company_id WHERE co.demo", Long.class)).isEqualTo(demoPaymentsBefore);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS r13_fail_demo_payment ON payment");
            jdbc.execute("DROP FUNCTION IF EXISTS r13_reject_demo_seed()");
        }
    }

    private Demo enter(Browser anonymous) throws Exception {
        var response = request("POST", "/api/v1/demo/sessions", anonymous.cookie(), anonymous.csrf(), null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonNodeBody body = new JsonNodeBody(json(response));
        Browser authenticated = csrf(response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0]);
        return new Demo(authenticated, UUID.fromString(body.company()), UUID.fromString(body.operator()),
                jdbc.queryForObject("SELECT id FROM customer WHERE company_id=? ORDER BY name LIMIT 1", UUID.class, UUID.fromString(body.company())));
    }

    private record JsonNodeBody(tools.jackson.databind.JsonNode node) {
        String company() { return node.path("company").path("id").asString(); }
        String operator() { return node.path("user").path("id").asString(); }
    }
}
