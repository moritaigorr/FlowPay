package com.flowpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.flowpay.config.demo.DemoCleanupScheduler;
import com.flowpay.company.CompanyProvisioning;
import com.flowpay.company.NewCompany;
import com.flowpay.payment.PaymentOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DemoLifecycleIT extends PostgresApplicationIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired DemoCleanupScheduler cleanup;
    @Autowired CompanyProvisioning companyProvisioning;
    @MockitoSpyBean PaymentOperations payments;
    private final HttpClient client = HttpClient.newHttpClient();
    private final List<UUID> createdCompanies = new ArrayList<>();

    record BrowserSession(String cookie, String csrf) {}
    record Demo(BrowserSession session, UUID companyId, UUID userId, String email, Instant createdAt, Instant expiresAt) {}

    @AfterEach
    void expireCreatedDemos() {
        createdCompanies.forEach(id -> jdbc.update("UPDATE company SET expires_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?", id));
        createdCompanies.clear();
    }

    @Test
    void demoEntryRequiresCsrfEvenWithoutAuthentication() throws Exception {
        var browser = anonymousSession();
        var before = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class);
        var response = request("POST", "/api/v1/demo/sessions", browser.cookie(), null, null);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(json(response).path("code").asString()).isEqualTo("CSRF_INVALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class)).isEqualTo(before);
    }

    @Test
    void demoCapacityReservationRequiresAnOuterTransaction() {
        var before = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class);
        var accidentallyCreated = new AtomicReference<UUID>();
        try {
            assertThatThrownBy(() -> companyProvisioning.createDemoIfAvailable(500)
                    .ifPresent(company -> accidentallyCreated.set(company.id())))
                    .isInstanceOf(IllegalTransactionStateException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class)).isEqualTo(before);
        } finally {
            if (accidentallyCreated.get() != null) {
                jdbc.update("DELETE FROM company WHERE id=?", accidentallyCreated.get());
            }
        }
    }

    @Test
    void createsIsolatedPasswordlessDemoAndReusesOnlyItsValidCurrentSession() throws Exception {
        var first = enterDemo(anonymousSession());
        assertThat(first.statusCode()).isEqualTo(201);
        var demo = demo(first);
        assertThat(demo.expiresAt()).isEqualTo(demo.createdAt().plus(Duration.ofHours(24)));
        assertThat(jdbc.queryForObject("SELECT demo FROM app_user WHERE id=?", Boolean.class, demo.userId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, demo.userId())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, demo.companyId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE company_id=?", Long.class, demo.companyId())).isEqualTo(4);
        // Four creation events, two processing events, and one cancellation event.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE company_id=?", Long.class, demo.companyId())).isEqualTo(7);

        var csrf = csrfFor(demo.session().cookie());
        var reused = request("POST", "/api/v1/demo/sessions", demo.session().cookie(), csrf.csrf(), null);
        assertThat(reused.statusCode()).isEqualTo(200);
        var same = demo(reused);
        assertThat(same.companyId()).isEqualTo(demo.companyId());
        assertThat(same.userId()).isEqualTo(demo.userId());
        assertThat(same.expiresAt()).isEqualTo(demo.expiresAt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, demo.companyId())).isEqualTo(4);

        var second = enterDemo(anonymousSession());
        assertThat(second.statusCode()).isEqualTo(201);
        var other = demo(second);
        assertThat(other.companyId()).isNotEqualTo(demo.companyId());
        assertThat(other.userId()).isNotEqualTo(demo.userId());
        assertThat(other.email()).isNotEqualTo(demo.email());

        var anon = anonymousSession();
        var login = request("POST", "/api/v1/auth/login", anon.cookie(), anon.csrf(),
                mapper.writeValueAsString(Map.of("email", demo.email(), "password", "anything")));
        assertThat(login.statusCode()).isEqualTo(401);
    }

    @Test
    void expiredDemoSessionGetsExplicitExpiryAndCannotReadCompanyData() throws Exception {
        var created = enterDemo(anonymousSession());
        assertThat(created.statusCode()).isEqualTo(201);
        var demo = demo(created);
        jdbc.update("UPDATE company SET expires_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?", demo.companyId());
        var response = request("GET", "/api/v1/auth/me", demo.session().cookie(), null, null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json(response).path("code").asString()).isEqualTo("DEMO_EXPIRED");
    }

    @Test
    void failureDuringSeedingRollsBackCompanyOperatorCustomersAndPayments() throws Exception {
        var before = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class);
        var usersBefore = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE demo", Long.class);
        var customersBefore = jdbc.queryForObject("SELECT count(*) FROM customer", Long.class);
        var paymentsBefore = jdbc.queryForObject("SELECT count(*) FROM payment", Long.class);
        var eventsBefore = jdbc.queryForObject("SELECT count(*) FROM payment_event", Long.class);
        doThrow(new IllegalStateException("synthetic seed failure")).when(payments).create(any(), any(), any(), any());
        var response = enterDemo(anonymousSession());
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE demo", Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE demo", Long.class)).isEqualTo(usersBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer", Long.class)).isEqualTo(customersBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Long.class)).isEqualTo(paymentsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event", Long.class)).isEqualTo(eventsBefore);
    }

    @Test
    void activeCapacityReturns503WithoutCreatingPartialDemo() throws Exception {
        var before = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo AND expires_at>CURRENT_TIMESTAMP", Long.class);
        var usersBefore = jdbc.queryForObject("""
                SELECT count(*) FROM app_user u JOIN company c ON c.id=u.company_id
                WHERE u.demo AND c.demo AND c.expires_at>CURRENT_TIMESTAMP
                """, Long.class);
        var marker = "Capacity fixture " + UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO company(id,name,demo,expires_at)
                    SELECT gen_random_uuid(),? || n,true,CURRENT_TIMESTAMP + interval '1 day'
                    FROM generate_series(1,500) n
                    """, marker);
            var response = enterDemo(anonymousSession());
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.headers().firstValue("Retry-After")).contains("60");
            assertThat(json(response).path("code").asString()).isEqualTo("DEMO_CAPACITY_REACHED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE demo AND expires_at>CURRENT_TIMESTAMP", Long.class))
                    .isEqualTo(before + 500);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM app_user u JOIN company c ON c.id=u.company_id
                    WHERE u.demo AND c.demo AND c.expires_at>CURRENT_TIMESTAMP
                    """, Long.class)).isEqualTo(usersBefore);
        } finally {
            jdbc.update("DELETE FROM company WHERE name LIKE ?", marker + "%");
        }
    }

    @Test
    void scheduledCleanupDeletesExpiredDemoRecordsAndSessionsButKeepsConventionalCompany() throws Exception {
        var expired = demo(enterDemo(anonymousSession()));
        var conventional = companyProvisioning.create(new NewCompany("Conventional preserved", false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name=?", Long.class,
                expired.userId().toString())).isGreaterThan(0);
        jdbc.update("UPDATE company SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?", expired.companyId());

        cleanup.cleanExpiredDemos();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE id=?", Long.class, expired.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE company_id=?", Long.class, expired.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, expired.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE company_id=?", Long.class, expired.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE company_id=?", Long.class, expired.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_record WHERE company_id=?", Long.class, expired.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name=?", Long.class,
                expired.userId().toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE id=? AND demo=false", Long.class, conventional.id())).isEqualTo(1);
        var oldSession = request("GET", "/api/v1/auth/me", expired.session().cookie(), null, null);
        assertThat(oldSession.statusCode()).isEqualTo(401);
        assertThat(json(oldSession).path("code").asString()).isEqualTo("UNAUTHENTICATED");
        jdbc.update("DELETE FROM company WHERE id=?", conventional.id());
    }

    private BrowserSession anonymousSession() throws Exception { return csrfFor(null); }
    private BrowserSession csrfFor(String cookie) throws Exception {
        var response = request("GET", "/api/v1/auth/csrf", cookie, null, null);
        return new BrowserSession(cookie(response, cookie), json(response).path("token").asString());
    }
    private HttpResponse<String> enterDemo(BrowserSession browser) throws Exception {
        return request("POST", "/api/v1/demo/sessions", browser.cookie(), browser.csrf(), null);
    }
    private Demo demo(HttpResponse<String> response) throws Exception {
        JsonNode body = json(response);
        var company = body.path("company");
        var user = body.path("user");
        UUID companyId = UUID.fromString(company.path("id").asString());
        createdCompanies.add(companyId);
        return new Demo(new BrowserSession(cookie(response), ""), companyId,
                UUID.fromString(user.path("id").asString()), user.path("email").asString(),
                Instant.parse(company.path("createdAt").asString()), Instant.parse(company.path("expiresAt").asString()));
    }
    private HttpResponse<String> request(String method, String path, String cookie, String csrf, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) builder.header("Cookie", cookie);
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        var request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    private String cookie(HttpResponse<String> response) { return cookie(response, null); }
    private String cookie(HttpResponse<String> response, String fallback) {
        return response.headers().firstValue("Set-Cookie").map(value -> value.split(";", 2)[0]).orElse(fallback);
    }
    private JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
}
