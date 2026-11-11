package com.flowpay;

import com.flowpay.payment.NewPayment;
import com.flowpay.payment.SimulationScenario;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentIdempotencyIT extends PaymentTestSupport {
    @Test
    void replayReturnsExactOriginalSnapshotAfterStatusChangesAndFreshRequestId() throws Exception {
        var session = login();
        var first = create(session, "snapshot", body(session));
        assertThat(first.statusCode()).isEqualTo(201);
        UUID payment = UUID.fromString(json(first).get("id").asString());
        jdbc.update("UPDATE payment SET status='APPROVED',updated_at=CURRENT_TIMESTAMP WHERE id=?", payment);
        var replay = create(session, "snapshot", body(session));
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(replay.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
        assertThat(replay.headers().firstValue("Idempotency-Replayed")).hasValue("true");
        assertThat(replay.headers().firstValue("X-Request-Id")).isNotEqualTo(first.headers().firstValue("X-Request-Id"));
        assertThat(count("payment", session)).isEqualTo(1);
        assertThat(count("payment_event", session)).isEqualTo(1);
    }

    @Test
    void canonicalHashUsesContractOrderEscapingUnicodeAndNormalizedDto() throws Exception {
        var session = login();
        String description = "Synthetic 🔒 \"quote\"\\slash\nline";
        var command = new NewPayment(session.customer(), new BigDecimal("12.3"), "BRL", "\u00a0" + description + "\u2003", SimulationScenario.APPROVE);
        var first = payments.create(session.company(), session.actor(), "canonical", command);
        assertThat(first.httpStatus()).isEqualTo(201);
        String canonical = "{\"customerId\":\"" + session.customer() + "\",\"amount\":\"12.30\",\"currency\":\"BRL\",\"description\":" + mapper.writeValueAsString(description) + ",\"simulationScenario\":\"APPROVE\"}";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("SELECT request_hash FROM idempotency_record WHERE company_id=?", String.class, session.company())).isEqualTo(hash);
        var replay = payments.create(session.company(), session.actor(), "canonical", new NewPayment(session.customer(), new BigDecimal("12.30"), "BRL", description, SimulationScenario.APPROVE));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.responseJson()).isEqualTo(first.responseJson());
        var absent = create(session, "empty", body(session).replace("12.30", "1"));
        for (String extra : new String[]{",\"description\":null", ",\"description\":\" \u00a0\""}) {
            var normalized = create(session, "empty", body(session).replace("12.30", "1.00").replace("}", extra + "}"));
            assertThat(normalized.body()).isEqualTo(absent.body());
            assertThat(normalized.headers().firstValue("Idempotency-Replayed")).hasValue("true");
        }
    }

    @Test
    void tenantScopedCaseSensitiveKeysAndHashConflictPrecedeOwnership() throws Exception {
        var session = login(); var foreign = login();
        assertThat(create(session, "shared", body(session)).statusCode()).isEqualTo(201);
        assertThat(create(foreign, "shared", body(foreign)).statusCode()).isEqualTo(201);
        assertThat(create(session, "SHARED", body(session)).statusCode()).isEqualTo(201);
        for (String changed : new String[]{body(session).replace("12.30", "12.31"), body(session).replace("APPROVE", "DECLINE"),
                body(session).replace(session.customer().toString(), foreign.customer().toString()), body(session).replace("}", ",\"description\":\"Changed\"}")}) {
            var conflict = create(session, "shared", changed);
            assertThat(conflict.statusCode()).isEqualTo(409);
            assertThat(conflict.body()).contains("IDEMPOTENCY_KEY_REUSED");
        }
        assertThat(count("payment", session)).isEqualTo(2);
    }

    @Test
    void conventionalConcurrentSameKeyWaitsOnUniqueIndexWithoutCompanyLock() throws Exception {
        var session = login();
        var inserted = new CountDownLatch(1); var release = new CountDownLatch(1);
        var owner = CompletableFuture.supplyAsync(() -> new TransactionTemplate(transactions).execute(status -> {
            var first = payments.create(session.company(), session.actor(), "concurrent", command(session));
            inserted.countDown(); await(release); return first;
        }));
        await(inserted);
        var waiter = CompletableFuture.supplyAsync(() -> payments.create(session.company(), session.actor(), "concurrent", command(session)));
        try { awaitLock("idempotency_record"); } finally { release.countDown(); }
        var first = owner.get(10, TimeUnit.SECONDS); var replay = waiter.get(10, TimeUnit.SECONDS);
        assertThat(first.replayed()).isFalse(); assertThat(replay.replayed()).isTrue();
        assertThat(replay.responseJson()).isEqualTo(first.responseJson());
        assertThat(count("payment", session)).isEqualTo(1);
        assertThat(count("payment_event", session)).isEqualTo(1);
        assertThat(count("idempotency_record", session)).isEqualTo(1);
    }

    @Test
    void uniqueIndexTimeoutReturns503AndRetryReplaysCommittedOwner() throws Exception {
        var session = login();
        var inserted = new CountDownLatch(1); var release = new CountDownLatch(1);
        var owner = CompletableFuture.supplyAsync(() -> new TransactionTemplate(transactions).execute(status -> {
            var first = payments.create(session.company(), session.actor(), "timeout", command(session));
            inserted.countDown(); await(release); return first;
        }));
        await(inserted);
        try {
            var timedOut = create(session, "timeout", body(session));
            assertThat(timedOut.statusCode()).isEqualTo(503);
            assertThat(timedOut.headers().firstValue("Retry-After")).hasValue("1");
            assertThat(timedOut.body()).contains("CONTENTION_TIMEOUT");
        } finally { release.countDown(); }
        var first = owner.get(10, TimeUnit.SECONDS);
        assertThat(create(session, "timeout", body(session)).body()).isEqualTo(first.responseJson());
        assertThat(count("payment", session)).isEqualTo(1);
    }

    @Test
    void injectedEventAndResponseFailuresRollbackAllWritesAndAllowSameKeyRetry() throws Exception {
        for (String table : new String[]{"payment_event", "idempotency_record"}) {
            var session = login();
            String fixture = "fail_payment_" + UUID.randomUUID().toString().replace("-", "");
            // Test-only PostgreSQL trigger: fail after payment insertion or after event insertion.
            jdbc.execute("CREATE FUNCTION " + fixture + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.company_id='" + session.company() + "'::uuid THEN RAISE EXCEPTION 'Synthetic injected failure'; END IF; RETURN NEW; END $$");
            jdbc.execute("CREATE TRIGGER " + fixture + " BEFORE " + (table.equals("payment_event") ? "INSERT" : "UPDATE") + " ON " + table + " FOR EACH ROW EXECUTE FUNCTION " + fixture + "()");
            try {
                var failed = create(session, "rollback", body(session));
                assertThat(failed.statusCode()).isEqualTo(500);
                assertThat(failed.body()).contains("INTERNAL_ERROR").doesNotContain("Synthetic injected failure", "INSERT", "UPDATE", "SQLException");
                assertThat(count("payment", session)).isZero();
                assertThat(count("payment_event", session)).isZero();
                assertThat(count("idempotency_record", session)).isZero();
            } finally {
                jdbc.execute("DROP TRIGGER " + fixture + " ON " + table);
                jdbc.execute("DROP FUNCTION " + fixture + "()");
            }
            assertThat(create(session, "rollback", body(session)).statusCode()).isEqualTo(201);
            assertThat(count("payment", session)).isEqualTo(1);
        }
    }
}
