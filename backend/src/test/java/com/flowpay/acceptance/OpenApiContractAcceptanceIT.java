package com.flowpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/** Guards that the maintained OpenAPI artifact describes real mappings and observed wire shapes. */
class OpenApiContractAcceptanceIT extends AcceptanceHttpSupport {
    JsonNode contract;
    static YAMLMapper yaml;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired tools.jackson.databind.json.JsonMapper jsonMapper;

    @BeforeAll
    static void initializeYamlMapper() {
        yaml = YAMLMapper.builder().build();
    }

    @BeforeEach
    void readContract() throws Exception {
        Object parsed = yaml.readValue(Files.readAllBytes(Path.of("../docs/api/openapi.yaml")), Object.class);
        contract = jsonMapper.valueToTree(parsed);
    }

    @Test
    void everyBusinessOperationMatchesAnActualSpringMapping() {
        Set<String> documented = new HashSet<>();
        contract.path("paths").properties().forEach(path -> {
            if (!path.getKey().startsWith("/api/v1/")) return;
            path.getValue().properties().forEach(operation -> documented.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey()));
        });
        Set<String> actual = new HashSet<>();
        mappings.getHandlerMethods().keySet().forEach(mapping -> mapping.getPatternValues().forEach(path -> {
            if (!path.startsWith("/api/v1/")) return;
            mapping.getMethodsCondition().getMethods().forEach(method -> actual.add(method.name() + " " + path));
        }));
        assertThat(actual).as("Spring API mappings should be specified once in the canonical contract").containsExactlyInAnyOrderElementsOf(documented);
    }

    @Test
    void observedSuccessAndProblemBodiesConformToTheirDocumentedSchemas() throws Exception {
        Operator merchant = operator("R13 contract");
        var identity = request("GET", "/api/v1/auth/me", merchant.browser().cookie(), null, null, null);
        assertThat(identity.statusCode()).isEqualTo(200);
        conforms("Session", json(identity));
        conformsResponse("/api/v1/auth/me", "get", "200", json(identity));

        String email = "contract-" + UUID.randomUUID() + "@example.test";
        var customer = request("POST", "/api/v1/customers", merchant.browser().cookie(), merchant.browser().csrf(), null,
                mapper.writeValueAsString(java.util.Map.of("name", "Contract customer", "email", email)));
        assertThat(customer.statusCode()).isEqualTo(201);
        conforms("Customer", json(customer));
        conformsResponse("/api/v1/customers", "post", "201", json(customer));

        String key = "openapi-" + UUID.randomUUID();
        String payload = "{\"customerId\":\"" + json(customer).path("id").asString() + "\",\"amount\":\"21.45\",\"currency\":\"BRL\",\"simulationScenario\":\"APPROVE\"}";
        var created = request("POST", "/api/v1/payments", merchant.browser().cookie(), merchant.browser().csrf(), key, payload);
        assertThat(created.statusCode()).isEqualTo(201);
        conforms("Payment", json(created));
        conformsResponse("/api/v1/payments", "post", "201", json(created));
        String id = json(created).path("id").asString();
        var detail = request("GET", "/api/v1/payments/" + id, merchant.browser().cookie(), null, null, null);
        conforms("Payment", json(detail));
        conformsResponse("/api/v1/payments/{id}", "get", "200", json(detail));
        var page = request("GET", "/api/v1/payments?page=0&size=20", merchant.browser().cookie(), null, null, null);
        conforms("PaymentPage", json(page));
        conformsResponse("/api/v1/payments", "get", "200", json(page));
        var transition = request("POST", "/api/v1/payments/" + id + "/process", merchant.browser().cookie(), merchant.browser().csrf(), null, null);
        conforms("Payment", json(transition));
        conformsResponse("/api/v1/payments/{id}/process", "post", "200", json(transition));
        var history = request("GET", "/api/v1/payments/" + id + "/events", merchant.browser().cookie(), null, null, null);
        conformsArray("PaymentEvent", json(history));
        conformsResponse("/api/v1/payments/{id}/events", "get", "200", json(history));
        String range = "?from=2020-01-01T00%3A00%3A00Z&to=2030-01-01T00%3A00%3A00Z";
        var summary = request("GET", "/api/v1/dashboard/summary" + range, merchant.browser().cookie(), null, null, null);
        conforms("DashboardSummary", json(summary));
        conformsResponse("/api/v1/dashboard/summary", "get", "200", json(summary));
        var finalConflict = request("POST", "/api/v1/payments/" + id + "/process", merchant.browser().cookie(), merchant.browser().csrf(), null, null);
        assertThat(finalConflict.statusCode()).isEqualTo(409);
        conforms("Problem", json(finalConflict));
        conformsResponse("/api/v1/payments/{id}/process", "post", "409", json(finalConflict));
    }

    private void conformsResponse(String path, String method, String status, JsonNode body) {
        JsonNode response = contract.path("paths").path(path).path(method).path("responses").path(status);
        while (response.has("$ref")) response = resolve(response.path("$ref").asText());
        JsonNode content = response.path("content");
        JsonNode schema = content.has("application/problem+json")
                ? content.path("application/problem+json").path("schema")
                : content.path("application/json").path("schema");
        assertThat(schema.isMissingNode()).as(method + " " + path + " response " + status + " documents JSON schema").isFalse();
        validateSchema(schema, body, method.toUpperCase(Locale.ROOT) + " " + path + " " + status);
    }

    private JsonNode resolve(String reference) {
        if (!reference.startsWith("#/")) throw new AssertionError("Unsupported nonlocal response reference " + reference);
        JsonNode resolved = contract;
        for (String part : reference.substring(2).split("/")) resolved = resolved.path(part);
        return resolved;
    }

    private void conforms(String schema, JsonNode body) {
        validate("#/components/schemas/" + schema, body, "response");
    }

    private void conformsArray(String schema, JsonNode body) {
        assertThat(body.isArray()).isTrue();
        body.forEach(item -> conforms(schema, item));
    }

    private void validate(String reference, JsonNode value, String at) {
        if (reference.startsWith("#/")) {
            validateSchema(resolve(reference), value, at);
        } else throw new AssertionError("Unsupported nonlocal schema reference " + reference);
    }

    private void validateSchema(JsonNode schema, JsonNode value, String at) {
        if (schema.has("$ref")) {
            validate(schema.path("$ref").asText(), value, at);
            return;
        }
        if (value.isNull()) {
            assertThat(schema.path("nullable").asBoolean(false)).as(at + " nullability").isTrue();
            return;
        }
        JsonNode enumeration = schema.path("enum");
        if (enumeration.isArray()) assertThat(contains(enumeration, value)).as(at + " enum value " + value).isTrue();
        switch (schema.path("type").asText()) {
            case "object" -> {
                assertThat(value.isObject()).as(at + " object type").isTrue();
                JsonNode required = schema.path("required");
                required.forEach(field -> assertThat(value.has(field.asText())).as(at + " required " + field.asText()).isTrue());
                JsonNode properties = schema.path("properties");
                if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean()) {
                    value.properties().forEach(field -> assertThat(properties.has(field.getKey())).as(at + " undocumented field " + field.getKey()).isTrue());
                }
                value.properties().forEach(field -> {
                    if (properties.has(field.getKey())) validateSchema(properties.path(field.getKey()), field.getValue(), at + "." + field.getKey());
                });
            }
            case "array" -> {
                assertThat(value.isArray()).as(at + " array type").isTrue();
                value.forEach(item -> validateSchema(schema.path("items"), item, at + "[]"));
            }
            case "string" -> {
                assertThat(value.isTextual()).as(at + " string type").isTrue();
                String text = value.asText();
                if (schema.path("format").asText().equals("uuid")) assertThat(org.springframework.util.StringUtils.hasText(text) && isUuid(text)).as(at + " UUID format").isTrue();
                if (schema.path("format").asText().equals("date-time")) assertThat(isInstant(text)).as(at + " date-time format").isTrue();
            }
            case "integer" -> assertThat(value.isIntegralNumber()).as(at + " integer type").isTrue();
            case "number" -> assertThat(value.isNumber()).as(at + " number type").isTrue();
            case "boolean" -> assertThat(value.isBoolean()).as(at + " boolean type").isTrue();
            default -> { }
        }
    }

    private boolean contains(JsonNode values, JsonNode item) {
        for (JsonNode value : values) if (value.equals(item)) return true;
        return false;
    }
    private boolean isUuid(String value) { try { UUID.fromString(value); return true; } catch (IllegalArgumentException ignored) { return false; } }
    private boolean isInstant(String value) { try { Instant.parse(value); return true; } catch (RuntimeException ignored) { return false; } }
}
