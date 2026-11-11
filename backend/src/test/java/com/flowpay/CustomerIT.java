package com.flowpay;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.flowpay.customer.CustomerAccess;
import com.flowpay.customer.CustomerOperations;
import com.flowpay.customer.NewCustomer;
import com.flowpay.customer.CustomerNotFoundException;
import com.flowpay.company.CompanyAccess;
import com.flowpay.company.CompanyUnavailableException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerIT extends PostgresApplicationIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired CustomerAccess access;
    @Autowired CustomerOperations operations;
    @Autowired CompanyAccess companies;
    @Autowired PlatformTransactionManager transactions;
    final HttpClient client = HttpClient.newHttpClient();
    record Session(UUID companyId, String cookie, String csrf) {}

    Session login() throws Exception {
        UUID company = UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO company(id,name) VALUES (?,?)", company, "Synthetic customer company");
        jdbc.update("INSERT INTO app_user(id,company_id,email,password_hash,demo) VALUES (?,?,?,?,false)",
                UUID.randomUUID(), company, email, "{pbkdf2@SpringSecurity_v5_8}" +
                Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("customer test"));
        var anonymous = request("GET", "/api/v1/auth/csrf", null, null, null);
        var authenticated = request("POST", "/api/v1/auth/login", cookie(anonymous), json(anonymous).get("token").asString(),
                mapper.writeValueAsString(Map.of("email", email, "password", "customer test")));
        assertThat(authenticated.statusCode()).isEqualTo(200);
        String cookie = cookie(authenticated);
        var csrf = request("GET", "/api/v1/auth/csrf", cookie, null, null);
        return new Session(company, cookie, json(csrf).get("token").asString());
    }

    HttpResponse<String> request(String method, String path, String cookie, String csrf, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) builder.header("Cookie", cookie);
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String cookie(HttpResponse<String> response) { return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0]; }
    JsonNode json(HttpResponse<String> response) { return mapper.readTree(response.body()); }
    HttpResponse<String> create(Session session, String name, String email) throws Exception {
        return request("POST", "/api/v1/customers", session.cookie(), session.csrf(), mapper.writeValueAsString(Map.of("name", name, "email", email)));
    }
    HttpResponse<String> list(Session session, String query) throws Exception {
        return request("GET", "/api/v1/customers" + query, session.cookie(), null, null);
    }
    String query(String q) { return "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8); }

    @Test
    void createsNormalizedCustomersAndAllowsDuplicateEmails() throws Exception {
        var session = login();
        var created = create(session, "\u00a0\u2003Synthetic 🔒\u00a0", "\u00a0USER@EXAMPLE.TEST\u2003");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(json(created).get("name").asString()).isEqualTo("Synthetic 🔒");
        assertThat(json(created).get("email").asString()).isEqualTo("user@example.test");
        assertThat(json(created).properties()).hasSize(5);
        assertThat(created.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(created.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(create(session, "Second", "user@example.test").statusCode()).isEqualTo(201);
        assertThat(json(list(session, "")).get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void pagesAreTenantScopedAndHighPagesKeepActualTotals() throws Exception {
        var first = login();
        var second = login();
        create(first, "First", "first@example.test");
        create(first, "Second", "second@example.test");
        create(second, "Foreign", "foreign@example.test");
        var page = list(first, "?page=0&size=1");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(json(page).get("totalElements").asLong()).isEqualTo(2);
        assertThat(json(page).get("totalPages").asInt()).isEqualTo(2);
        assertThat(json(page).get("items").size()).isEqualTo(1);
        assertThat(page.body()).doesNotContain("Foreign");
        var beyond = json(list(first, "?page=2147483647&size=100"));
        assertThat(beyond.get("items").isEmpty()).isTrue();
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(2);
        assertThat(json(list(second, query("absent"))).get("totalPages").asInt()).isZero();
    }

    @Test
    void searchIsCaseInsensitiveLiteralSubstringIncludingEscapeCharacters() throws Exception {
        var session = login();
        create(session, "Synthetic 50%_!\\ customer", "literal@example.test");
        create(session, "Synthetic 50 percent", "other@example.test");
        for (String q : new String[]{"%", "_", "!", "\\", "LITERAL@EXAMPLE", "  50%_  "}) {
            var response = list(session, query(q));
            assertThat(response.statusCode()).as(q).isEqualTo(200);
            assertThat(json(response).get("totalElements").asLong()).as(q).isEqualTo(1);
        }
        assertThat(json(list(session, query("\u00a0\u2003"))).get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void rejectsNullCharacterInSearchBeforeBindingPostgresText() throws Exception {
        var session = login();
        var response = list(session, "?q=%00");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        var problem = json(response);
        assertThat(problem.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(problem.get("fieldErrors").get(0).get("field").asString()).isEqualTo("q");
        assertThat(problem.get("fieldErrors").get(0).get("code").asString()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    void validationAndSecurityRejectUntrustedRequests() throws Exception {
        var session = login();
        assertThat(request("GET", "/api/v1/customers", null, null, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/v1/customers", session.cookie(), null, "{}").statusCode()).isEqualTo(403);
        for (String body : new String[]{"{}", "{\"name\":\"\u00a0\",\"email\":\"a@example.test\"}",
                "{\"name\":\"Name\",\"email\":\"invalid\"}",
                "{\"name\":\"Name\",\"email\":\"a@example.test\",\"companyId\":\"foreign\"}"}) {
            var response = request("POST", "/api/v1/customers", session.cookie(), session.csrf(), body);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("VALIDATION_FAILED", "fieldErrors");
        }
        assertThat(create(session, "🔒".repeat(120), "unicode@example.test").statusCode()).isEqualTo(201);
        assertThat(create(session, "🔒".repeat(121), "unicode@example.test").statusCode()).isEqualTo(400);
        for (String q : new String[]{"?page=-1", "?size=0", "?size=101", "?page=2147483648", "?page=wrong", query(" ".repeat(201))}) {
            assertThat(list(session, q).statusCode()).as(q).isEqualTo(400);
        }
    }

    @Test
    void facadeRequiresOwnedCustomerAndActiveCompanyAndJoinsOuterRollback() throws Exception {
        var session = login();
        var other = login();
        var created = operations.create(session.companyId(), new NewCustomer("Owned", "owned@example.test"));
        assertThat(access.requireOwned(session.companyId(), created.id()).id()).isEqualTo(created.id());
        assertThatThrownBy(() -> access.requireOwned(other.companyId(), created.id())).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> access.requireOwned(session.companyId(), UUID.randomUUID())).isInstanceOf(CustomerNotFoundException.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            operations.create(session.companyId(), new NewCustomer("Rollback", "rollback@example.test"));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, session.companyId())).isEqualTo(1);
        jdbc.update("UPDATE company SET demo=true,expires_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?", session.companyId());
        assertThatThrownBy(() -> access.requireOwned(session.companyId(), created.id())).isInstanceOf(CompanyUnavailableException.class);
        assertThatThrownBy(() -> operations.create(session.companyId(), new NewCustomer("Expired", "expired@example.test")))
                .isInstanceOf(CompanyUnavailableException.class);
        var expired = list(session, "");
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.body()).contains("DEMO_EXPIRED");
    }

    void demo(Session session, int count) {
        jdbc.update("UPDATE company SET demo=true,expires_at=CURRENT_TIMESTAMP + interval '1 hour' WHERE id=?", session.companyId());
        jdbc.update("""
                INSERT INTO customer(id,company_id,name,email)
                SELECT gen_random_uuid(),?, 'Synthetic quota customer', 'quota@example.test' FROM generate_series(1,?)
                """, session.companyId(), count);
    }

    void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Synchronization timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    void awaitDatabaseLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("SELECT count(*) > 0 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%company%'", Boolean.class)) return;
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError("Independent PostgreSQL connection did not wait for company lock");
    }

    @Test
    void simultaneousBoundaryCreationKeepsCompanyLockUntilOuterCommit() throws Exception {
        var session = login();
        demo(session, 99);
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var owner = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            operations.create(session.companyId(), new NewCustomer("Hundredth", "boundary@example.test"));
            inserted.countDown();
            await(release);
        }));
        await(inserted);
        var waiter = CompletableFuture.supplyAsync(() -> {
            try { return create(session, "Concurrent", "boundary@example.test"); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        });
        try { awaitDatabaseLock(); } finally { release.countDown(); }
        owner.get(10, TimeUnit.SECONDS);
        var rejected = waiter.get(10, TimeUnit.SECONDS);
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(rejected.body()).contains("DEMO_LIMIT_REACHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, session.companyId())).isEqualTo(100);
    }

    @Test
    void demoCreationRevalidatesExpiryAfterLockWait() throws Exception {
        var session = login();
        demo(session, 0);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var owner = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            companies.lockForCleanup(session.companyId());
            jdbc.update("UPDATE company SET expires_at=? WHERE id=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), session.companyId());
            locked.countDown();
            await(release);
        }));
        await(locked);
        var waiter = CompletableFuture.supplyAsync(() -> {
            try { return create(session, "Expired while waiting", "expired@example.test"); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        });
        try { awaitDatabaseLock(); } finally { release.countDown(); }
        owner.get(10, TimeUnit.SECONDS);
        var expired = waiter.get(10, TimeUnit.SECONDS);
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.body()).contains("DEMO_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, session.companyId())).isZero();
    }

    @Test
    void lockTimeoutHasRetryAfterAndDoesNotInsertPartialData() throws Exception {
        var session = login();
        demo(session, 0);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var owner = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            companies.lockForCleanup(session.companyId());
            locked.countDown();
            await(release);
        }));
        await(locked);
        try {
            var response = create(session, "Contended", "contended@example.test");
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains("CONTENTION_TIMEOUT");
            assertThat(response.headers().firstValue("Retry-After")).hasValue("1");
        } finally { release.countDown(); }
        owner.get(10, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id=?", Long.class, session.companyId())).isZero();
    }

    @Test
    void sameTimestampPagesUsePostgresUuidOrdering() throws Exception {
        var session = login();
        var low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        for (UUID id : new UUID[]{low, high}) jdbc.update("""
                INSERT INTO customer(id,company_id,name,email,created_at,updated_at)
                VALUES (?,?,'Tie','tie@example.test','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')
                """, id, session.companyId());
        assertThat(json(list(session, "?size=1")).get("items").get(0).get("id").asString()).isEqualTo(high.toString());
        assertThat(json(list(session, "?size=1&page=1")).get("items").get(0).get("id").asString()).isEqualTo(low.toString());
    }

    @Test
    void explicitEmptyPaginationValuesAreInvalid() throws Exception {
        var session = login();
        for (String query : new String[]{"?page=", "?size="}) {
            var response = list(session, query);
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(response.body()).contains("VALIDATION_FAILED", "fieldErrors");
        }
    }

    @Test
    void nonStringCustomerFieldsAreRejected() throws Exception {
        var session = login();
        for (String body : new String[]{"{\"name\":123,\"email\":\"valid@example.test\"}",
                "{\"name\":true,\"email\":\"valid@example.test\"}",
                "{\"name\":{},\"email\":\"valid@example.test\"}",
                "{\"name\":[],\"email\":\"valid@example.test\"}",
                "{\"name\":null,\"email\":\"valid@example.test\"}",
                "{\"name\":\"Valid\",\"email\":123}"}) {
            var response = request("POST", "/api/v1/customers", session.cookie(), session.csrf(), body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(response.body()).contains("VALIDATION_FAILED", "fieldErrors");
        }
    }

    @Test
    void limitsUseNormalizedCodepointsAndConventionalCompaniesHaveNoDemoQuota() throws Exception {
        var session = login();
        String maxEmail = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertThat(maxEmail.length()).isEqualTo(254);
        assertThat(create(session, "\u00a0" + "🔒".repeat(120) + "\u00a0", maxEmail).statusCode()).isEqualTo(201);
        assertThat(create(session, "Long email", maxEmail + "e").statusCode()).isEqualTo(400);
        assertThat(list(session, query("🔒".repeat(200))).statusCode()).isEqualTo(200);
        assertThat(list(session, query("🔒".repeat(201))).statusCode()).isEqualTo(400);
        jdbc.update("""
                INSERT INTO customer(id,company_id,name,email)
                SELECT gen_random_uuid(),?, 'Synthetic conventional customer', 'conventional@example.test' FROM generate_series(1,100)
                """, session.companyId());
        assertThat(create(session, "Above demo limit", "conventional@example.test").statusCode()).isEqualTo(201);
        assertThatThrownBy(() -> operations.create(UUID.randomUUID(), new NewCustomer("Missing", "missing@example.test")))
                .isInstanceOf(CompanyUnavailableException.class);
    }

    @Test
    void postgresRejectsInvalidCustomerLengthBlankNamesAndForeignCompany() throws Exception {
        var session = login();
        String insert = "INSERT INTO customer(id,company_id,name,email) VALUES (?,?,?,?)";
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), session.companyId(), " ", "valid@example.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), session.companyId(), "x".repeat(121), "valid@example.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), session.companyId(), "Valid", "x".repeat(255)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), "Valid", "valid@example.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
