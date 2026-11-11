package com.flowpay;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentConcurrencyIT extends PaymentTestSupport {
    @Test
    void simultaneousProcessAndCancelHaveExactlyOneFinalTransition() throws Exception {
        var session = login();
        var created = create(session, "race-" + UUID.randomUUID(), body(session));
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(json(created).get("id").asString());

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var process = executor.submit(() -> {
                ready.countDown();
                awaitConcurrent(start);
                return request("POST", "/api/v1/payments/" + id + "/process",
                        session.cookie(), session.csrf(), null, null);
            });
            var cancel = executor.submit(() -> {
                ready.countDown();
                awaitConcurrent(start);
                return request("POST", "/api/v1/payments/" + id + "/cancel",
                        session.cookie(), session.csrf(), null, null);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var processResponse = process.get(15, TimeUnit.SECONDS);
            var cancelResponse = cancel.get(15, TimeUnit.SECONDS);
            assertThat(java.util.List.of(processResponse.statusCode(), cancelResponse.statusCode()))
                    .containsExactlyInAnyOrder(200, 409);
            var success = processResponse.statusCode() == 200 ? processResponse : cancelResponse;
            var rejected = processResponse.statusCode() == 409 ? processResponse : cancelResponse;
            assertThat(json(rejected).get("code").asString()).isEqualTo("PAYMENT_FINAL_STATE");
            String finalStatus = json(success).get("status").asString();
            assertThat(finalStatus).isIn("APPROVED", "CANCELLED");
            assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE id=?", String.class, id)).isEqualTo(finalStatus);
            var events = jdbc.queryForList("SELECT sequence,previous_status,new_status FROM payment_event WHERE payment_id=? ORDER BY sequence", id);
            assertThat(events).hasSize(2);
            assertThat(events.get(1).get("sequence")).isEqualTo(2L);
            assertThat(events.get(1).get("previous_status")).isEqualTo("PENDING");
            assertThat(events.get(1).get("new_status")).isEqualTo(finalStatus);
        }
    }

    private void awaitConcurrent(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent request start timed out.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent request was interrupted.", exception);
        }
    }
}
