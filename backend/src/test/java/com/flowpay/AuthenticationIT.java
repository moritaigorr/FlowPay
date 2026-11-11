package com.flowpay;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationIT extends PostgresApplicationIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    final HttpClient client = HttpClient.newHttpClient();

    record Fixture(UUID companyId, UUID userId, String email, String password) {}

    Fixture operator(String password) {
        UUID company = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String email = user + "@example.test";
        jdbc.update("INSERT INTO company(id,name) VALUES (?,?)", company, "Synthetic company");
        jdbc.update("INSERT INTO app_user(id,company_id,email,password_hash,demo) VALUES (?,?,?,?,false)",
                user, company, email, "{pbkdf2@SpringSecurity_v5_8}" + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(password));
        return new Fixture(company, user, email, password);
    }

    HttpResponse<String> request(String method, String path, String cookie, String csrf, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) request.header("Cookie", cookie);
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    String cookie(HttpResponse<String> response) {
        return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }

    String token(HttpResponse<String> response) {
        return mapper.readTree(response.body()).get("token").asString();
    }

    String credentials(Fixture fixture) {
        return mapper.writeValueAsString(java.util.Map.of("email", fixture.email(), "password", fixture.password()));
    }

    @Test
    void anonymousAccessAndWritesRequireAuthenticationAndCsrf() throws Exception {
        assertThat(request("GET", "/api/v1/auth/me", null, null, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/v1/company", null, null, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/v1/auth/login", null, null, "{}").statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/v1/auth/logout", null, "invalid", null).statusCode()).isEqualTo(403);
        var csrf = request("GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        assertThat(csrf.body()).contains("X-CSRF-TOKEN", "_csrf");
        assertThat(csrf.headers().firstValue("Set-Cookie").orElseThrow())
                .contains("SESSION=", "Path=/", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void loginRotatesPersistedSessionAndCsrfAndLogoutInvalidatesIt() throws Exception {
        var fixture = operator("  secure 🔒 password  ");
        var csrf = request("GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        String oldCookie = cookie(csrf);
        var login = request("POST", "/api/v1/auth/login", oldCookie, token(csrf), credentials(fixture));
        assertThat(login.statusCode()).isEqualTo(200);
        String authenticated = cookie(login);
        assertThat(authenticated).isNotEqualTo(oldCookie);
        JsonNode body = mapper.readTree(login.body());
        assertThat(body.get("user").get("id").asString()).isEqualTo(fixture.userId().toString());
        assertThat(body.get("user").has("companyId")).isFalse();
        assertThat(body.get("company").get("id").asString()).isEqualTo(fixture.companyId().toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ? AND max_inactive_interval = 1800",
                Long.class, fixture.userId().toString())).isEqualTo(1L);
        assertThat(request("GET", "/api/v1/auth/me", oldCookie, null, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/v1/auth/logout", authenticated, token(csrf), null).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/v1/company?companyId=" + UUID.randomUUID(), authenticated, null, null).body())
                .contains(fixture.companyId().toString());
        var fresh = request("GET", "/api/v1/auth/csrf", authenticated, null, null);
        assertThat(request("POST", "/api/v1/auth/logout", authenticated, token(fresh), null).statusCode()).isEqualTo(204);
        assertThat(request("GET", "/api/v1/auth/me", authenticated, null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void normalizesEmailButPreservesLongUnicodePasswordsAndRejectsBadCredentials() throws Exception {
        var fixture = operator("🔒".repeat(100));
        var csrf = request("GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        String normalized = mapper.writeValueAsString(java.util.Map.of("email", "\u00a0\u2003" + fixture.email().toUpperCase(java.util.Locale.ROOT) + "\u2003\u00a0",
                "password", fixture.password()));
        assertThat(request("POST", "/api/v1/auth/login", cookie(csrf), token(csrf), normalized).statusCode()).isEqualTo(200);
        var another = request("GET", "/api/v1/auth/csrf", null, null, null);
        var wrong = request("POST", "/api/v1/auth/login", cookie(another), token(another),
                mapper.writeValueAsString(java.util.Map.of("email", fixture.email(), "password", fixture.password() + "x")));
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(wrong.body()).contains("INVALID_CREDENTIALS").doesNotContain(fixture.password());
    }

    @Test
    void validationRejectsMissingOversizedAndUnknownFields() throws Exception {
        var csrf = request("GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        for (String body : new String[]{"{}", "{\"email\":\"bad\",\"password\":\"a\"}",
                "{\"email\":\"test@example.test\",\"password\":\"" + "x".repeat(129) + "\"}",
                "{\"email\":\"test@example.test\",\"password\":\"a\",\"companyId\":\"foreign\"}"}) {
            var response = request("POST", "/api/v1/auth/login", cookie(csrf), token(csrf), body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(response.body()).contains("VALIDATION_FAILED", "fieldErrors");
        }
    }

    @Test
    void idleSessionExpiresWithoutSleeping() throws Exception {
        var fixture = operator("idle test password");
        var csrf = request("GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        var login = request("POST", "/api/v1/auth/login", cookie(csrf), token(csrf), credentials(fixture));
        assertThat(login.statusCode()).isEqualTo(200);
        jdbc.update("UPDATE spring_session SET last_access_time = ?, expiry_time = ? WHERE principal_name = ?",
                System.currentTimeMillis() - 1_801_000, System.currentTimeMillis() - 1000, fixture.userId().toString());
        assertThat(request("GET", "/api/v1/auth/me", cookie(login), null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void passwordlessDemoAndUnknownOperatorsHaveTheSameCredentialFailure() throws Exception {
        var fixture = operator("demo cannot log in");
        jdbc.update("UPDATE app_user SET demo=true,password_hash=null WHERE id=?", fixture.userId());
        var csrf = request("GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        for (String email : new String[]{fixture.email(), UUID.randomUUID() + "@example.test"}) {
            var response = request("POST", "/api/v1/auth/login", cookie(csrf), token(csrf),
                    mapper.writeValueAsString(java.util.Map.of("email", email, "password", "wrong")));
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("INVALID_CREDENTIALS");
        }
    }

    @Test
    void expiredCompanyInvalidatesPreviouslyAuthenticatedSession() throws Exception {
        var fixture = operator("expiry test");
        var csrf = request("GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        var login = request("POST", "/api/v1/auth/login", cookie(csrf), token(csrf), credentials(fixture));
        assertThat(login.statusCode()).isEqualTo(200);
        jdbc.update("UPDATE company SET demo=true, expires_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?", fixture.companyId());
        var expired = request("GET", "/api/v1/company", cookie(login), null, null);
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.body()).contains("DEMO_EXPIRED");
        assertThat(request("GET", "/api/v1/auth/me", cookie(login), null, null).body()).contains("UNAUTHENTICATED");
    }

    @Test
    void missingCompanyInvalidatesAnExistingSession() throws Exception {
        var fixture = operator("missing company");
        var csrf = request("GET", "/api/v1/auth/csrf", null, null, null);
        var login = request("POST", "/api/v1/auth/login", cookie(csrf), token(csrf), credentials(fixture));
        assertThat(login.statusCode()).isEqualTo(200);
        jdbc.update("DELETE FROM app_user WHERE id=?", fixture.userId());
        jdbc.update("DELETE FROM company WHERE id=?", fixture.companyId());
        var missing = request("GET", "/api/v1/company", cookie(login), null, null);
        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(missing.body()).contains("UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name=?", Long.class,
                fixture.userId().toString())).isZero();
    }
}

@org.springframework.test.context.ActiveProfiles("prod")
class ProductionCookieIT extends PostgresApplicationIT {
    @LocalServerPort int port;

    @Test
    void productionSessionCookieIsSecureEvenWhenTheBackendConnectionIsHttp() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Set-Cookie").orElseThrow())
                .contains("SESSION=", "Secure", "HttpOnly", "Path=/", "SameSite=Lax");
    }
}
