package com.flowpay;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiDocumentationIT extends PostgresApplicationIT {
    @LocalServerPort
    int port;
    @Autowired
    JsonMapper mapper;
    final HttpClient client = HttpClient.newHttpClient();

    record InputProbe(String name) {}

    @Test
    void configuredJsonMapperRejectsUnknownInputFields() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"name\":\"Synthetic\",\"companyId\":\"untrusted\"}", InputProbe.class))
                .isInstanceOf(tools.jackson.databind.exc.UnrecognizedPropertyException.class);
    }

    HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Request-Id", "untrusted-client-id").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void canonicalSchemaIsServedByteForByte() throws Exception {
        var response = get("/api/openapi.yaml");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/yaml");
        assertThat(response.body()).isEqualTo(Files.readAllBytes(Path.of("../docs/api/openapi.yaml")));
        UUID.fromString(response.headers().firstValue("X-Request-Id").orElseThrow());
    }

    @Test
    void documentationAndLocalAssetsArePublicAndReadOnly() throws Exception {
        var response = get("/api/docs");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("/swagger-ui/swagger-ui-bundle.js", "/swagger-ui/flowpay-init.js");
        for (String asset : new String[]{"swagger-ui.css", "swagger-ui-bundle.js", "flowpay-init.js"}) {
            assertThat(get("/swagger-ui/" + asset).statusCode()).as(asset).isEqualTo(200);
        }
        assertThat(new String(get("/swagger-ui/flowpay-init.js").body(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("url: '/api/openapi.yaml'", "supportedSubmitMethods: []", "persistAuthorization: false");
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(401);
    }

    @Test
    void healthIsBoundedAndBusinessRoutesFailClosed() throws Exception {
        var health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(new String(health.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("{\"status\":\"UP\"}");
        var denied = get("/api/v1/payments?password=secret");
        assertThat(denied.statusCode()).isEqualTo(401);
        assertThat(denied.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(new String(denied.body(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("UNAUTHENTICATED", "requestId", "about:blank").doesNotContain("secret", "password");
        var post = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(post.statusCode()).isEqualTo(403);
        assertThat(post.body()).contains("CSRF_INVALID");
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
    }
}
