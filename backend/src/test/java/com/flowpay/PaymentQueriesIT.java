package com.flowpay;

import com.flowpay.company.CompanyUnavailableException;
import com.flowpay.payment.PaymentAccess;
import com.flowpay.payment.PaymentFilter;
import com.flowpay.payment.PaymentListing;
import com.flowpay.payment.PaymentNotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

class PaymentQueriesIT extends PaymentTestSupport {
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05Z");
    @MockitoSpyBean JdbcTemplate queryJdbc;
    @Autowired PaymentAccess access;
    @Autowired PaymentListing listing;

    JsonNode get(Session session, String suffix) throws Exception {
        var response = request("GET", "/api/v1/payments" + suffix, session.cookie(), null, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        return json(response);
    }

    UUID insert(Session session, String id, Instant created, String status) {
        UUID payment = UUID.fromString(id);
        jdbc.update("""
                INSERT INTO payment(id,company_id,customer_id,amount,currency,status,simulation_scenario,created_at,updated_at)
                VALUES (?,?,?,99999999999999999.99,'BRL',?,'APPROVE',?,?)
                """, payment, session.company(), session.customer(), status, Timestamp.from(created), Timestamp.from(created));
        return payment;
    }

    UUID insert(Session session, Instant created, String status) {
        return insert(session, UUID.randomUUID().toString(), created, status);
    }

    String encoded(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    List<String> ids(JsonNode page) {
        var result = new ArrayList<String>();
        page.get("items").forEach(item -> result.add(item.get("id").asString()));
        return result;
    }

    @Test
    void readsCurrentPaymentUsingExactWireShapeAndDecimalPrecision() throws Exception {
        var session = login();
        var id = insert(session, CREATED, "APPROVED");
        var payment = get(session, "/" + id.toString().toUpperCase());
        assertThat(payment.properties()).hasSize(9);
        assertThat(payment.get("id").asString()).isEqualTo(id.toString());
        assertThat(payment.get("customerId").asString()).isEqualTo(session.customer().toString());
        assertThat(payment.get("amount").isString()).isTrue();
        assertThat(payment.get("amount").asString()).isEqualTo("99999999999999999.99");
        assertThat(payment.get("status").asString()).isEqualTo("APPROVED");
        assertThat(payment.get("description").isNull()).isTrue();
        assertThat(payment.get("currency").asString()).isEqualTo("BRL");
        assertThat(payment.get("simulationScenario").asString()).isEqualTo("APPROVE");
        assertThat(payment.get("createdAt").asString()).isEqualTo(CREATED.toString());
        assertThat(payment.get("updatedAt").asString()).isEqualTo(CREATED.toString());
    }

    @Test
    void hidesForeignAndUnknownDetailsAndHistoryIncludingPaymentsWithoutEvents() throws Exception {
        var own = login();
        var foreign = login();
        var foreignPayment = insert(foreign, CREATED, "PENDING");
        for (UUID id : List.of(foreignPayment, UUID.randomUUID())) {
            for (String suffix : List.of("/" + id, "/" + id + "/events")) {
                var response = request("GET", "/api/v1/payments" + suffix, own.cookie(), null, null, null);
                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(json(response).get("code").asString()).isEqualTo("PAYMENT_NOT_FOUND");
            }
        }
        assertThat(get(own, "").get("totalElements").asLong()).isZero();
    }

    @Test
    void readsHistoryBySequenceInsteadOfTimestampWithExactEventShape() throws Exception {
        var session = login();
        var response = create(session, "history", body(session));
        UUID id = UUID.fromString(json(response).get("id").asString());
        jdbc.update("UPDATE payment_event SET created_at=? WHERE payment_id=?", Timestamp.from(CREATED.plusSeconds(5)), id);
        jdbc.update("""
                INSERT INTO payment_event(id,company_id,payment_id,sequence,previous_status,new_status,reason_code,actor_user_id,created_at)
                VALUES (?,?,?,2,'PENDING','REJECTED','SIMULATED_DECLINE',?,?)
                """, UUID.randomUUID(), session.company(), id, session.actor(), Timestamp.from(CREATED));
        var events = get(session, "/" + id + "/events");
        assertThat(events.size()).isEqualTo(2);
        assertThat(events.get(0).get("sequence").asLong()).isEqualTo(1);
        assertThat(events.get(1).get("sequence").asLong()).isEqualTo(2);
        for (var event : events) {
            assertThat(event.properties()).hasSize(8);
            assertThat(event.get("paymentId").asString()).isEqualTo(id.toString());
            assertThat(event.get("actorUserId").asString()).isEqualTo(session.actor().toString());
            assertThat(event.get("createdAt").asString()).endsWith("Z");
            assertThat(UUID.fromString(event.get("id").asString())).isNotNull();
        }
        assertThat(events.get(0).get("previousStatus").isNull()).isTrue();
        assertThat(events.get(0).get("newStatus").asString()).isEqualTo("PENDING");
        assertThat(events.get(0).get("reasonCode").isNull()).isTrue();
        assertThat(events.get(1).get("previousStatus").asString()).isEqualTo("PENDING");
        assertThat(events.get(1).get("newStatus").asString()).isEqualTo("REJECTED");
        assertThat(events.get(1).get("reasonCode").asString()).isEqualTo("SIMULATED_DECLINE");
    }

    @Test
    void defaultsToTwentyAndSupportsOneHundredWithCoherentHighAndEmptyPages() throws Exception {
        var session = login();
        for (int i = 0; i < 23; i++) insert(session, CREATED.plusSeconds(i), "PENDING");
        var page = get(session, "");
        assertThat(page.properties()).hasSize(5);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("items").size()).isEqualTo(20);
        assertThat(page.get("totalElements").asLong()).isEqualTo(23);
        assertThat(page.get("totalPages").asLong()).isEqualTo(2);
        assertThat(get(session, "?size=100").get("items").size()).isEqualTo(23);
        var second = get(session, "?page=1");
        assertThat(second.get("items").size()).isEqualTo(3);
        assertThat(ids(page)).doesNotContainAnyElementsOf(ids(second));
        var high = get(session, "?page=2147483647&size=100");
        assertThat(high.get("items").isEmpty()).isTrue();
        assertThat(high.get("totalElements").asLong()).isEqualTo(23);
        assertThat(high.get("totalPages").asLong()).isEqualTo(1);
        var empty = get(session, "?status=CANCELLED");
        assertThat(empty.get("items").isEmpty()).isTrue();
        assertThat(empty.get("totalElements").asLong()).isZero();
        assertThat(empty.get("totalPages").asLong()).isZero();
    }

    @Test
    void listUsesOneRelationalQueryForOneOrManyRowsWithoutCustomerLookups() throws Exception {
        var session = login();
        for (int i = 0; i < 23; i++) insert(session, CREATED.plusSeconds(i), "PENDING");
        for (int size : List.of(1, 100)) {
            clearInvocations(queryJdbc);
            assertThat(get(session, "?q=SYNTHETIC&size=" + size).get("items").size()).isEqualTo(Math.min(size, 23));
            // Count the terminal query overload, not its delegating convenience methods.
            var queries = mockingDetails(queryJdbc).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("query"))
                    .filter(call -> call.getMethod().getParameterCount() == 3)
                    .filter(call -> call.getMethod().getParameterTypes()[0] == PreparedStatementCreator.class).toList();
            assertThat(queries).as("One bounded payment query; no per-row customer query").hasSize(1);
        }
    }

    @Test
    void publicReadFacadesEnforceActiveCompanyAndOwnershipAndReturnImmutableLists() throws Exception {
        var session = login();
        var foreign = login();
        var id = UUID.fromString(json(create(session, "facade", body(session))).get("id").asString());
        var filter = new PaymentFilter(null, null, null, null, null);
        assertThat(access.requireOwned(session.company(), id).id()).isEqualTo(id);
        assertThatThrownBy(() -> access.requireOwned(foreign.company(), id)).isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> access.events(foreign.company(), id)).isInstanceOf(PaymentNotFoundException.class);
        assertThat(listing.list(foreign.company(), 0, 20, filter).items()).isEmpty();
        assertThatThrownBy(() -> access.events(session.company(), id).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> listing.list(session.company(), 0, 20, filter).items().clear()).isInstanceOf(UnsupportedOperationException.class);
        jdbc.update("UPDATE company SET demo=true,expires_at=CURRENT_TIMESTAMP-interval '1 minute' WHERE id=?", session.company());
        assertThatThrownBy(() -> access.requireOwned(session.company(), id)).isInstanceOf(CompanyUnavailableException.class);
        assertThatThrownBy(() -> access.events(session.company(), id)).isInstanceOf(CompanyUnavailableException.class);
        assertThatThrownBy(() -> listing.list(session.company(), 0, 20, filter)).isInstanceOf(CompanyUnavailableException.class);
    }

    @Test
    void breaksTimestampTiesUsingPostgresUuidOrderAcrossPages() throws Exception {
        var session = login();
        String low = "00000000-0000-0000-0000-000000000001";
        String middle = "7fffffff-ffff-ffff-ffff-ffffffffffff";
        String high = "80000000-0000-0000-0000-000000000000";
        insert(session, low, CREATED, "PENDING");
        insert(session, high, CREATED, "PENDING");
        insert(session, middle, CREATED, "PENDING");
        assertThat(ids(get(session, "?size=2"))).containsExactly(high, middle);
        assertThat(ids(get(session, "?size=2&page=1"))).containsExactly(low);
    }

    @Test
    void combinesStatusCustomerAndInclusiveExclusiveInstantFilters() throws Exception {
        var session = login();
        insert(session, CREATED.minusSeconds(1), "PENDING");
        var included = insert(session, CREATED, "PENDING");
        insert(session, CREATED.plusSeconds(1), "APPROVED");
        insert(session, CREATED.plusSeconds(2), "PENDING");
        String from = encoded("2026-01-02T06:04:05+03:00");
        String to = encoded(CREATED.plusSeconds(2).toString());
        assertThat(ids(get(session, "?status=PENDING&customerId=" + session.customer() + "&createdFrom=" + from + "&createdTo=" + to)))
                .containsExactly(included.toString());
        assertThat(get(session, "?createdTo=" + encoded(CREATED.toString())).get("totalElements").asLong()).isEqualTo(1);
        assertThat(get(session, "?createdFrom=" + from).get("totalElements").asLong()).isEqualTo(3);
        assertThat(get(session, "?createdFrom=" + from + "&size=1").get("totalPages").asLong()).isEqualTo(3);
    }

    @Test
    void preservesIntervalBoundariesBelowPostgresMicrosecondPrecision() throws Exception {
        var session = login();
        insert(session, CREATED, "PENDING");
        for (int nanos : List.of(-100, 0, 100)) {
            String boundary = encoded(CREATED.plusNanos(nanos).toString());
            assertThat(get(session, "?createdFrom=" + boundary).get("totalElements").asLong()).isEqualTo(nanos > 0 ? 0 : 1);
            assertThat(get(session, "?createdTo=" + boundary).get("totalElements").asLong()).isEqualTo(nanos > 0 ? 1 : 0);
        }
    }

    @Test
    void foreignUnknownAndDeletedCustomerFiltersReturnEmptyPages() throws Exception {
        var session = login();
        var foreign = login();
        insert(session, CREATED, "PENDING");
        insert(foreign, CREATED, "PENDING");
        UUID deleted = UUID.randomUUID();
        jdbc.update("INSERT INTO customer(id,company_id,name,email) VALUES (?,?,'Deleted','deleted@example.test')", deleted, session.company());
        jdbc.update("DELETE FROM customer WHERE id=?", deleted);
        for (UUID id : List.of(foreign.customer(), UUID.randomUUID(), deleted)) {
            var page = get(session, "?customerId=" + id);
            assertThat(page.get("items").isEmpty()).isTrue();
            assertThat(page.get("totalElements").asLong()).isZero();
            assertThat(page.get("totalPages").asLong()).isZero();
        }
    }

    @Test
    void searchesLiteralCaseInsensitiveCustomerNameEmailOrExactUuidAndAndsOtherFilters() throws Exception {
        var session = login();
        jdbc.update("UPDATE customer SET name='Acme %_!\\ Shop',email='mixed.search@example.test' WHERE id=?", session.customer());
        var id = insert(session, CREATED, "PENDING");
        var otherCustomer = UUID.randomUUID();
        jdbc.update("INSERT INTO customer(id,company_id,name,email) VALUES (?,?,'Unrelated','other@example.test')", otherCustomer, session.company());
        var other = insert(session, CREATED.plusSeconds(1), "APPROVED");
        jdbc.update("UPDATE payment SET customer_id=? WHERE id=?", otherCustomer, other);
        for (String search : List.of("acME", "MIXED.SEARCH", "%_!\\", "\u2003acme\u2003", id.toString().toUpperCase())) {
            assertThat(ids(get(session, "?q=" + encoded(search)))).containsExactly(id.toString());
        }
        assertThat(get(session, "?q=" + encoded("\u2003 \u2003")).get("totalElements").asLong()).isEqualTo(2);
        assertThat(ids(get(session, "?q=" + other.toString().toUpperCase()))).containsExactly(other.toString());
        assertThat(get(session, "?q=" + id + "&status=APPROVED").get("totalElements").asLong()).isZero();
        assertThat(get(session, "?q=" + id + "&customerId=" + otherCustomer).get("totalElements").asLong()).isZero();
        assertThat(get(session, "?q=" + id + "&createdFrom=" + encoded(CREATED.plusSeconds(1).toString())).get("totalElements").asLong()).isZero();
        assertThat(get(session, "?q=" + encoded("' OR true --")).get("totalElements").asLong()).isZero();
    }

    @Test
    void enforcesSearchCodePointLimitBeforeStripping() throws Exception {
        var session = login();
        assertThat(get(session, "?q=" + encoded("\uD83D\uDE00".repeat(200))).get("items").isEmpty()).isTrue();
        for (String q : List.of("a".repeat(201), " ".repeat(201), "\uD83D\uDE00".repeat(201))) {
            invalid(session, "?q=" + encoded(q), "q");
        }
    }

    @Test
    void rejectsNullCharacterInSearchBeforeBindingPostgresText() throws Exception {
        var session = login();
        var response = request("GET", "/api/v1/payments?q=%00", session.cookie(), null, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        var problem = json(response);
        assertThat(problem.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(problem.get("fieldErrors").get(0).get("field").asString()).isEqualTo("q");
        assertThat(problem.get("fieldErrors").get(0).get("code").asString()).isEqualTo("INVALID_FORMAT");
    }

    void invalid(Session session, String suffix, String field) throws Exception {
        var response = request("GET", "/api/v1/payments" + suffix, session.cookie(), null, null, null);
        assertThat(response.statusCode()).as(suffix + ": " + response.body()).isEqualTo(400);
        var problem = json(response);
        assertThat(problem.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(problem.get("fieldErrors").get(0).get("field").asString()).isEqualTo(field);
    }

    @Test
    void rejectsMalformedOutOfRangeAndEmptyDocumentedQueryValues() throws Exception {
        var session = login();
        for (String value : List.of("-1", "", "2147483648", "99999999999999999999999", "1.0", "%20", "+1")) invalid(session, "?page=" + value, "page");
        for (String value : List.of("0", "-1", "101", "", "2147483648")) invalid(session, "?size=" + value, "size");
        for (String value : List.of("", "pending", "UNKNOWN")) invalid(session, "?status=" + value, "status");
        for (String value : List.of("", "invalid", "1-1-1-1-1")) invalid(session, "?customerId=" + value, "customerId");
        for (String field : List.of("createdFrom", "createdTo")) {
            for (String value : List.of("", "2026-01-02", "2026-01-02T03:04:05", "2026-02-30T00:00:00Z", "bad")) invalid(session, "?" + field + "=" + value, field);
        }
        invalid(session, "?createdFrom=" + CREATED + "&createdTo=" + CREATED, "createdTo");
        invalid(session, "?createdFrom=" + CREATED.plusSeconds(1) + "&createdTo=" + CREATED, "createdTo");
        invalid(session, "/1-1-1-1-1", "id");
        invalid(session, "/invalid/events", "id");
    }

    @Test
    void deniesAnonymousAndExpiredDemoReads() throws Exception {
        var session = login();
        var id = insert(session, CREATED, "PENDING");
        for (String suffix : List.of("", "/" + id, "/" + id + "/events")) {
            assertThat(request("GET", "/api/v1/payments" + suffix, null, null, null, null).statusCode()).isEqualTo(401);
        }
        for (String suffix : List.of("", "/" + id, "/" + id + "/events")) {
            var expired = login();
            jdbc.update("UPDATE company SET demo=true,expires_at=CURRENT_TIMESTAMP-interval '1 minute' WHERE id=?", expired.company());
            var response = request("GET", "/api/v1/payments" + suffix, expired.cookie(), null, null, null);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(json(response).get("code").asString()).isEqualTo("DEMO_EXPIRED");
        }
    }
}
