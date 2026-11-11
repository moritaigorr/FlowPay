package com.flowpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import com.flowpay.config.demo.DemoCleanupScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DemoConcurrencyIT extends PostgresApplicationIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired DemoCleanupScheduler cleanup;
    private final HttpClient client = HttpClient.newHttpClient();
    private final List<UUID> createdCompanies = new ArrayList<>();

    record Browser(String cookie, String csrf) {}

    @org.junit.jupiter.api.AfterEach
    void expireCreatedDemos() {
        createdCompanies.forEach(id -> jdbc.update("UPDATE company SET expires_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?", id));
        createdCompanies.clear();
    }

    @Test
    void concurrentAnonymousVisitorsReceiveSeparateIsolatedCompanies() throws Exception {
        var first = anonymous();
        var second = anonymous();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return enter(first); });
            var b = pool.submit(() -> { start.await(); return enter(second); });
            start.countDown();
            var responseA = a.get(10, TimeUnit.SECONDS);
            var responseB = b.get(10, TimeUnit.SECONDS);
            assertThat(List.of(responseA.statusCode(), responseB.statusCode())).containsExactlyInAnyOrder(201, 201);
            UUID companyA = company(responseA), companyB = company(responseB);
            createdCompanies.add(companyA);
            createdCompanies.add(companyB);
            assertThat(companyA).isNotEqualTo(companyB);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE id IN (?,?) AND demo", Long.class, companyA, companyB)).isEqualTo(2);
        }
    }

    @Test
    void capacityReservationAllowsOnlyOneConcurrentVisitorAtTheLastSlot() throws Exception {
        long existing = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo AND expires_at>CURRENT_TIMESTAMP", Long.class);
        assertThat(existing).isLessThan(500);
        int filler = Math.toIntExact(499 - existing);
        var marker = "Capacity race " + UUID.randomUUID();
        var usersBefore = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE demo", Long.class);
        try {
            if (filler > 0) jdbc.update("""
                    INSERT INTO company(id,name,demo,expires_at)
                    SELECT gen_random_uuid(),? || n,true,CURRENT_TIMESTAMP + interval '1 day'
                    FROM generate_series(1,?) n
                    """, marker, filler);
            var first = anonymous();
            var second = anonymous();
            var start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> { start.await(); return enter(first); });
                var b = pool.submit(() -> { start.await(); return enter(second); });
                start.countDown();
                var responseA = a.get(15, TimeUnit.SECONDS);
                var responseB = b.get(15, TimeUnit.SECONDS);
                assertThat(List.of(responseA.statusCode(), responseB.statusCode())).containsExactlyInAnyOrder(201, 503);
                var created = responseA.statusCode() == 201 ? responseA : responseB;
                var rejected = responseA.statusCode() == 503 ? responseA : responseB;
                var createdId = company(created);
                createdCompanies.add(createdId);
                assertThat(mapper.readTree(rejected.body()).path("code").asString()).isEqualTo("DEMO_CAPACITY_REACHED");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE demo AND expires_at>CURRENT_TIMESTAMP", Long.class)).isEqualTo(500);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE demo", Long.class)).isEqualTo(usersBefore + 1);
            }
        } finally {
            jdbc.update("DELETE FROM company WHERE name LIKE ?", marker + "%");
        }
    }

    @Test
    void expiryCleanupRacingWithACompanyWriteLeavesNoPartialCustomerOrDeadlock() throws Exception {
        var browser = anonymous();
        var entered = enter(browser);
        assertThat(entered.statusCode()).isEqualTo(201);
        var body = mapper.readTree(entered.body());
        UUID companyId = UUID.fromString(body.path("company").path("id").asString());
        UUID operatorId = UUID.fromString(body.path("user").path("id").asString());
        createdCompanies.add(companyId);
        String sessionCookie = cookie(entered);
        var authenticated = anonymousFor(sessionCookie);
        jdbc.update("UPDATE company SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?", companyId);

        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var write = pool.submit(() -> {
                start.await();
                return request("POST", "/api/v1/customers", authenticated.cookie(), authenticated.csrf(),
                        "{\"name\":\"Race customer\",\"email\":\"race-" + operatorId + "@example.test\"}");
            });
            var cleaning = pool.submit(() -> { start.await(); cleanup.cleanExpiredDemos(); return true; });
            start.countDown();
            var response = write.get(10, TimeUnit.SECONDS);
            cleaning.get(10, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE id=?", Long.class, companyId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, companyId)).isZero();
        }
    }

    private Browser anonymous() throws Exception {
        var response = request("GET", "/api/v1/auth/csrf", null, null);
        return new Browser(cookie(response), mapper.readTree(response.body()).path("token").asString());
    }
    private Browser anonymousFor(String cookie) throws Exception {
        var response = request("GET", "/api/v1/auth/csrf", cookie, null);
        return new Browser(cookie(response, cookie), mapper.readTree(response.body()).path("token").asString());
    }
    private HttpResponse<String> enter(Browser browser) throws Exception {
        return request("POST", "/api/v1/demo/sessions", browser.cookie(), browser.csrf());
    }
    private UUID company(HttpResponse<String> response) throws Exception {
        JsonNode body = mapper.readTree(response.body());
        return UUID.fromString(body.path("company").path("id").asString());
    }
    private HttpResponse<String> request(String method, String path, String cookie, String csrf) throws Exception {
        return request(method, path, cookie, csrf, null);
    }
    private HttpResponse<String> request(String method, String path, String cookie, String csrf, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) builder.header("Cookie", cookie);
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String cookie(HttpResponse<String> response) { return cookie(response, null); }
    private String cookie(HttpResponse<String> response, String fallback) {
        return response.headers().firstValue("Set-Cookie").map(value -> value.split(";", 2)[0]).orElse(fallback);
    }
}
