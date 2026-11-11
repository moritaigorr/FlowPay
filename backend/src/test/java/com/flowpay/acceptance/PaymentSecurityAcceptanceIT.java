package com.flowpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Independent consumer-level journey; deliberately uses only HTTP and persisted effects. */
class PaymentSecurityAcceptanceIT extends AcceptanceHttpSupport {
    @Test
    void csrfLoginCustomerCreateReplayProcessHistoryAndFinalConflict() throws Exception {
        Browser anonymous = anonymous();
        var csrfDenied = request("POST", "/api/v1/customers", anonymous.cookie(), null, null,
                "{\"name\":\"No CSRF\",\"email\":\"no-csrf@example.test\"}");
        assertThat(csrfDenied.statusCode()).isEqualTo(403);
        assertThat(json(csrfDenied).path("code").asString()).isEqualTo("CSRF_INVALID");

        Operator merchant = operator("R13 merchant");
        String email = "journey-" + UUID.randomUUID() + "@example.test";
        var customerResponse = request("POST", "/api/v1/customers", merchant.browser().cookie(), merchant.browser().csrf(), null,
                mapper.writeValueAsString(java.util.Map.of("name", "Acceptance customer", "email", email)));
        assertThat(customerResponse.statusCode()).isEqualTo(201);
        UUID customer = UUID.fromString(json(customerResponse).path("id").asString());
        assertThat(json(customerResponse).path("email").asString()).isEqualTo(email);

        String key = "r13-" + UUID.randomUUID();
        String payload = "{\"customerId\":\"" + customer + "\",\"amount\":\"14.20\",\"currency\":\"BRL\",\"simulationScenario\":\"APPROVE\"}";
        var created = request("POST", "/api/v1/payments", merchant.browser().cookie(), merchant.browser().csrf(), key, payload);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Idempotency-Replayed")).hasValue("false");
        assertThat(json(created).path("amount").asString()).isEqualTo("14.20");
        UUID payment = UUID.fromString(json(created).path("id").asString());

        var replay = request("POST", "/api/v1/payments", merchant.browser().cookie(), merchant.browser().csrf(), key, payload);
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("Idempotency-Replayed")).hasValue("true");
        assertThat(replay.body()).isEqualTo(created.body());
        assertThat(replay.headers().firstValue("Location")).isEqualTo(created.headers().firstValue("Location"));

        var processed = request("POST", "/api/v1/payments/" + payment + "/process", merchant.browser().cookie(), merchant.browser().csrf(), null, null);
        assertThat(processed.statusCode()).isEqualTo(200);
        assertThat(json(processed).path("status").asString()).isEqualTo("APPROVED");
        var events = request("GET", "/api/v1/payments/" + payment + "/events", merchant.browser().cookie(), null, null, null);
        assertThat(events.statusCode()).isEqualTo(200);
        assertThat(json(events).size()).isEqualTo(2);
        assertThat(json(events).get(0).path("newStatus").asString()).isEqualTo("PENDING");
        assertThat(json(events).get(1).path("previousStatus").asString()).isEqualTo("PENDING");
        assertThat(json(events).get(1).path("newStatus").asString()).isEqualTo("APPROVED");
        var finalConflict = request("POST", "/api/v1/payments/" + payment + "/process", merchant.browser().cookie(), merchant.browser().csrf(), null, null);
        assertThat(finalConflict.statusCode()).isEqualTo(409);
        assertThat(json(finalConflict).path("code").asString()).isEqualTo("PAYMENT_FINAL_STATE");
    }

    @Test
    void separateCompanySessionsCannotReadOrChangeForeignPayment() throws Exception {
        Operator first = operator("R13 first");
        Operator second = operator("R13 second");
        String key = "tenant-" + UUID.randomUUID();
        var created = request("POST", "/api/v1/payments", first.browser().cookie(), first.browser().csrf(), key, paymentBody(first));
        assertThat(created.statusCode()).isEqualTo(201);
        String id = json(created).path("id").asString();
        assertThat(request("GET", "/api/v1/payments/" + id, second.browser().cookie(), null, null, null).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/v1/payments/" + id + "/events", second.browser().cookie(), null, null, null).statusCode()).isEqualTo(404);
        var foreignTransition = request("POST", "/api/v1/payments/" + id + "/cancel", second.browser().cookie(), second.browser().csrf(), null, null);
        assertThat(foreignTransition.statusCode()).isEqualTo(404);
        assertThat(json(foreignTransition).path("code").asString()).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(request("GET", "/api/v1/payments/" + id, first.browser().cookie(), null, null, null).statusCode()).isEqualTo(200);
    }
}


abstract class AcceptanceHttpSupport extends PostgresApplicationIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    final HttpClient client = HttpClient.newHttpClient();

    record Browser(String cookie, String csrf) {}
    record Operator(UUID company, UUID user, UUID customer, String email, String password, Browser browser) {}

    Operator operator(String companyName) throws Exception {
        UUID company = UUID.randomUUID(), user = UUID.randomUUID(), customer = UUID.randomUUID();
        String email = user + "@acceptance.test", password = "R13 acceptance password";
        jdbc.update("INSERT INTO company(id,name) VALUES (?,?)", company, companyName + " " + company);
        jdbc.update("INSERT INTO app_user(id,company_id,email,password_hash,demo) VALUES (?,?,?,?,false)", user, company, email,
                "{pbkdf2@SpringSecurity_v5_8}" + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(password));
        jdbc.update("INSERT INTO customer(id,company_id,name,email) VALUES (?,?,?,?)", customer, company, "Acceptance customer", "customer-" + customer + "@example.test");
        Browser anonymous = anonymous();
        HttpResponse<String> login = request("POST", "/api/v1/auth/login", anonymous.cookie(), anonymous.csrf(), null,
                mapper.writeValueAsString(Map.of("email", email, "password", password)));
        if (login.statusCode() != 200) throw new AssertionError("Synthetic operator login failed: " + login.statusCode() + " " + login.body());
        String cookie = cookie(login);
        Browser authenticated = new Browser(cookie, csrf(cookie).csrf());
        return new Operator(company, user, customer, email, password, authenticated);
    }

    Browser anonymous() throws Exception { return csrf(null); }

    Browser csrf(String priorCookie) throws Exception {
        HttpResponse<String> response = request("GET", "/api/v1/auth/csrf", priorCookie, null, null, null);
        if (response.statusCode() != 200) throw new AssertionError("CSRF endpoint failed: " + response.statusCode());
        String returnedCookie = response.headers().firstValue("Set-Cookie").map(value -> value.split(";", 2)[0]).orElse(priorCookie);
        if (returnedCookie == null) throw new AssertionError("CSRF response did not establish or preserve a session cookie");
        return new Browser(returnedCookie, json(response).path("token").asString());
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

    String paymentBody(Operator operator) {
        return "{\"customerId\":\"" + operator.customer() + "\",\"amount\":\"14.20\",\"currency\":\"BRL\",\"simulationScenario\":\"APPROVE\"}";
    }

    String cookie(HttpResponse<String> response) { return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0]; }
    JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
}

