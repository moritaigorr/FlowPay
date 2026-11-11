package com.flowpay;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.ContractValidationException;
import com.flowpay.payment.NewPayment;
import com.flowpay.payment.PaymentOperations;
import com.flowpay.payment.SimulationScenario;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class PaymentTestSupport extends PostgresApplicationIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired PaymentOperations payments;
    @Autowired CompanyAccess companies;
    @Autowired PlatformTransactionManager transactions;
    final HttpClient client = HttpClient.newHttpClient();
    record Session(UUID company, UUID actor, UUID customer, String cookie, String csrf) {}

    Session login() throws Exception {
        UUID company = UUID.randomUUID(), actor = UUID.randomUUID(), customer = UUID.randomUUID();
        String email = actor + "@example.test";
        jdbc.update("INSERT INTO company(id,name) VALUES (?,?)", company, "Synthetic payment company");
        jdbc.update("INSERT INTO app_user(id,company_id,email,password_hash,demo) VALUES (?,?,?,?,false)",
                actor, company, email, "{pbkdf2@SpringSecurity_v5_8}" +
                Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("payment test"));
        jdbc.update("INSERT INTO customer(id,company_id,name,email) VALUES (?,?,'Synthetic customer','customer@example.test')", customer, company);
        var anonymous = request("GET", "/api/v1/auth/csrf", null, null, null, null);
        var authenticated = request("POST", "/api/v1/auth/login", cookie(anonymous), json(anonymous).get("token").asString(), null,
                mapper.writeValueAsString(Map.of("email", email, "password", "payment test")));
        assertThat(authenticated.statusCode()).isEqualTo(200);
        String cookie = cookie(authenticated);
        var csrf = request("GET", "/api/v1/auth/csrf", cookie, null, null, null);
        return new Session(company, actor, customer, cookie, json(csrf).get("token").asString());
    }
    HttpResponse<String> request(String method, String path, String cookie, String csrf, String key, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) builder.header("Cookie", cookie);
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (key != null) builder.header("Idempotency-Key", key);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String cookie(HttpResponse<String> response) { return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0]; }
    JsonNode json(HttpResponse<String> response) { return mapper.readTree(response.body()); }
    String body(Session session) {
        return "{\"customerId\":\"" + session.customer() + "\",\"amount\":\"12.30\",\"currency\":\"BRL\",\"simulationScenario\":\"APPROVE\"}";
    }
    HttpResponse<String> create(Session session, String key, String body) throws Exception {
        return request("POST", "/api/v1/payments", session.cookie(), session.csrf(), key, body);
    }
    NewPayment command(Session session) {
        return new NewPayment(session.customer(), new BigDecimal("12.30"), "BRL", null, SimulationScenario.APPROVE);
    }
    long count(String table, Session session) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE company_id=?", Long.class, session.company());
    }
    void demo(Session session, int count) {
        jdbc.update("UPDATE company SET demo=true,expires_at=CURRENT_TIMESTAMP + interval '1 hour' WHERE id=?", session.company());
        jdbc.update("""
                INSERT INTO payment(id,company_id,customer_id,amount,currency,status,simulation_scenario)
                SELECT gen_random_uuid(),?,?,'1.00','BRL','PENDING','APPROVE' FROM generate_series(1,?)
                """, session.company(), session.customer(), count);
    }
    void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).as("Synchronization completed").isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
    void awaitLock(String queryFragment) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("SELECT count(*) > 0 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE ?",
                    Boolean.class, "%" + queryFragment + "%")) return;
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError("Independent PostgreSQL connection did not wait for expected lock");
    }
}

class PaymentCreationIT extends PaymentTestSupport {
    @Test
    void createsPendingPaymentAndExactlyOneAtomicCreationEvent() throws Exception {
        var session = login();
        var response = create(session, "first", body(session));
        assertThat(response.statusCode()).isEqualTo(201);
        var payment = json(response);
        assertThat(payment.properties()).hasSize(9);
        assertThat(payment.get("status").asString()).isEqualTo("PENDING");
        assertThat(payment.get("amount").isString()).isTrue();
        assertThat(payment.get("amount").asString()).isEqualTo("12.30");
        assertThat(payment.get("description").isNull()).isTrue();
        assertThat(response.headers().firstValue("Location")).hasValue("/api/v1/payments/" + payment.get("id").asString());
        assertThat(response.headers().firstValue("Idempotency-Replayed")).hasValue("false");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(count("payment", session)).isEqualTo(1);
        var event = jdbc.queryForMap("SELECT * FROM payment_event WHERE company_id=?", session.company());
        assertThat(event.get("sequence")).isEqualTo(1L);
        assertThat(event.get("previous_status")).isNull();
        assertThat(event.get("new_status")).isEqualTo("PENDING");
        assertThat(event.get("reason_code")).isNull();
        assertThat(event.get("actor_user_id")).isEqualTo(session.actor());
        assertThat(event.get("payment_id").toString()).isEqualTo(payment.get("id").asString());
        assertThat(jdbc.queryForObject("SELECT response_json FROM idempotency_record WHERE company_id=?", String.class, session.company())).isEqualTo(response.body());
    }

    @Test
    void securityAndOwnershipComeFromSession() throws Exception {
        var session = login();
        var foreign = login();
        var anonymous = request("GET", "/api/v1/auth/csrf", null, null, null, null);
        assertThat(request("POST", "/api/v1/payments", cookie(anonymous), json(anonymous).get("token").asString(), "anon", body(session)).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/v1/payments", session.cookie(), null, "csrf", body(session)).statusCode()).isEqualTo(403);
        for (UUID customer : new UUID[]{foreign.customer(), UUID.randomUUID()}) {
            var response = create(session, customer.toString(), body(session).replace(session.customer().toString(), customer.toString()));
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).contains("CUSTOMER_NOT_FOUND");
        }
        assertThat(count("idempotency_record", session)).isZero();
    }

    @Test
    void rejectsStrictWireSyntaxBeforeCoercionAndUnknownFields() throws Exception {
        var session = login();
        for (String amount : new String[]{"12.30", "true", "null", "{}", "[]", "\"+1\"", "\"1e2\"", "\"01\"", "\"1.000\"", "\" 1\"", "\"0\"", "\"100000000000000000\""}) {
            var response = create(session, UUID.randomUUID().toString(), body(session).replace("\"12.30\"", amount));
            assertThat(response.statusCode()).as(amount).isEqualTo(400);
            assertThat(response.body()).contains("VALIDATION_FAILED", "fieldErrors");
        }
        for (String invalid : new String[]{"{}", "null", body(session).replace("\"BRL\"", "123"),
                body(session).replace("\"APPROVE\"", "true"), body(session).replace("APPROVE", "approve"),
                body(session).replace("BRL", "USD"), body(session).replace(session.customer().toString(), "1-1-1-1-1"),
                body(session).replace("}", ",\"companyId\":\"foreign\"}"),
                body(session).replace("}", ",\"description\":123}"),
                body(session).replace("}", ",\"description\":\"" + "🔒".repeat(501) + "\"}")}) {
            var response = create(session, UUID.randomUUID().toString(), invalid);
            assertThat(response.statusCode()).as(invalid).isEqualTo(400);
        }
        for (String key : new String[]{null, "", "has space", "x".repeat(129)}) {
            assertThat(create(session, key, body(session)).statusCode()).as(key).isEqualTo(400);
        }
        assertThat(count("payment", session)).isZero();
        assertThat(count("idempotency_record", session)).isZero();
    }

    @Test
    void validatesDirectBigDecimalCommandAndPreservesMaximum() throws Exception {
        var session = login();
        for (BigDecimal amount : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.000"), new BigDecimal("100000000000000000.00")}) {
            assertThatThrownBy(() -> payments.create(session.company(), session.actor(), "invalid", new NewPayment(session.customer(), amount, "BRL", null, SimulationScenario.APPROVE)))
                    .isInstanceOf(ContractValidationException.class);
        }
        for (String key : new String[]{"é", "\t", "a b", "x".repeat(129)}) {
            assertThatThrownBy(() -> payments.create(session.company(), session.actor(), key, command(session)))
                    .isInstanceOf(ContractValidationException.class);
        }
        var response = create(session, "maximum", body(session).replace("12.30", "99999999999999999.99"));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json(response).get("amount").asString()).isEqualTo("99999999999999999.99");
    }

    @Test
    void missingOrNullRequiredFieldsHaveRequiredMetadata() throws Exception {
        var session = login();
        for (String field : new String[]{"customerId", "amount", "currency", "simulationScenario"}) {
            for (boolean explicitNull : new boolean[]{false, true}) {
                var body = (tools.jackson.databind.node.ObjectNode) mapper.readTree(body(session));
                if (explicitNull) body.putNull(field); else body.remove(field);
                var response = create(session, "required", mapper.writeValueAsString(body));
                assertThat(response.statusCode()).as(field).isEqualTo(400);
                var error = json(response).get("fieldErrors").get(0);
                assertThat(error.get("field").asString()).as(field).isEqualTo(field);
                assertThat(error.get("code").asString()).as(field).isEqualTo("REQUIRED");
            }
        }
        assertThat(count("idempotency_record", session)).isZero();
    }

    @Test
    void directFacadeValidatesAllCommandFieldsAndUnicodeDescriptionBoundary() throws Exception {
        var session = login();
        assertThatThrownBy(() -> payments.create(session.company(), session.actor(), "null-command", null))
                .isInstanceOf(ContractValidationException.class);
        for (var invalid : new NewPayment[]{
                new NewPayment(null, BigDecimal.ONE, "BRL", null, SimulationScenario.APPROVE),
                new NewPayment(session.customer(), BigDecimal.ONE, null, null, SimulationScenario.APPROVE),
                new NewPayment(session.customer(), BigDecimal.ONE, "brl", null, SimulationScenario.APPROVE),
                new NewPayment(session.customer(), BigDecimal.ONE, "BRL", null, null),
                new NewPayment(session.customer(), BigDecimal.ONE, "BRL", "🔒".repeat(501), SimulationScenario.APPROVE)}) {
            assertThatThrownBy(() -> payments.create(session.company(), session.actor(), "invalid-command", invalid))
                    .isInstanceOf(ContractValidationException.class);
        }
        var created = payments.create(session.company(), session.actor(), "description-boundary",
                new NewPayment(session.customer(), BigDecimal.ONE, "BRL", "\u00a0" + "🔒".repeat(500) + "\u2003", SimulationScenario.DECLINE));
        assertThat(mapper.readTree(created.responseJson()).get("description").asString()).isEqualTo("🔒".repeat(500));
        assertThat(mapper.readTree(created.responseJson()).get("simulationScenario").asString()).isEqualTo("DECLINE");
    }

    @Test
    void restoresCallerLockTimeoutAndJoinsOuterRollbackForNewCreationAndReplay() throws Exception {
        var session = login(); demo(session, 0);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT set_config('lock_timeout','1500ms',true)", String.class);
            payments.create(session.company(), session.actor(), "outer-rollback", command(session));
            assertThat(jdbc.queryForObject("SELECT current_setting('lock_timeout')", String.class)).isEqualTo("1500ms");
            assertThat(payments.create(session.company(), session.actor(), "outer-rollback", command(session)).replayed()).isTrue();
            assertThat(jdbc.queryForObject("SELECT current_setting('lock_timeout')", String.class)).isEqualTo("1500ms");
            status.setRollbackOnly();
        });
        assertThat(count("payment", session)).isZero();
        assertThat(count("payment_event", session)).isZero();
        assertThat(count("idempotency_record", session)).isZero();
        assertThat(create(session, "outer-rollback", body(session)).statusCode()).isEqualTo(201);
    }

    @Test
    void concurrentDemoBoundaryAllows500thAndItsReplayWhileRejecting501st() throws Exception {
        var session = login();
        demo(session, 499);
        var inserted = new CountDownLatch(1); var release = new CountDownLatch(1);
        var owner = CompletableFuture.supplyAsync(() -> new TransactionTemplate(transactions).execute(status -> {
            var created = payments.create(session.company(), session.actor(), "500", command(session));
            inserted.countDown(); await(release); return created;
        }));
        await(inserted);
        var waiter = CompletableFuture.supplyAsync(() -> {
            try { return create(session, "501", body(session)); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        });
        try { awaitLock("company"); } finally { release.countDown(); }
        var first = owner.get(10, TimeUnit.SECONDS);
        assertThat(waiter.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(409);
        var replay = create(session, "500", body(session));
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(first.responseJson());
        assertThat(replay.headers().firstValue("Idempotency-Replayed")).hasValue("true");
        var conflict = create(session, "500", body(session).replace("12.30", "13.30"));
        assertThat(conflict.body()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(count("payment", session)).isEqualTo(500);
        assertThat(count("idempotency_record", session)).isEqualTo(1);
    }

    @Test
    void expiryIsRevalidatedAfterCompanyLockAndAlsoRejectsReplay() throws Exception {
        var session = login(); demo(session, 0);
        assertThat(create(session, "existing", body(session)).statusCode()).isEqualTo(201);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var owner = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            companies.lockForCleanup(session.company());
            jdbc.update("UPDATE company SET expires_at=? WHERE id=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), session.company());
            locked.countDown(); await(release);
        }));
        await(locked);
        var waiter = CompletableFuture.supplyAsync(() -> {
            try { return create(session, "existing", body(session)); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        });
        try { awaitLock("company"); } finally { release.countDown(); }
        owner.get(10, TimeUnit.SECONDS);
        var expired = waiter.get(10, TimeUnit.SECONDS);
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.body()).contains("DEMO_EXPIRED");
        assertThat(count("payment", session)).isEqualTo(1);
    }

    @Test
    void demoLockTimeoutReturns503AndLeavesNoReservation() throws Exception {
        var session = login(); demo(session, 0);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var owner = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            companies.lockForCleanup(session.company()); locked.countDown(); await(release);
        }));
        await(locked);
        try {
            var response = create(session, "timeout", body(session));
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains("CONTENTION_TIMEOUT");
            assertThat(response.headers().firstValue("Retry-After")).hasValue("1");
        } finally { release.countDown(); }
        owner.get(10, TimeUnit.SECONDS);
        assertThat(count("payment", session)).isZero();
        assertThat(count("idempotency_record", session)).isZero();
        assertThat(create(session, "timeout", body(session)).statusCode()).isEqualTo(201);
    }
}
