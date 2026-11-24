package com.flowpay.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowpay.observability.RequestCorrelationFilter;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class ApiSecurityProblemHandlerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ApiSecurityProblemHandler handler = new ApiSecurityProblemHandler(new ApiProblemWriter(json));

    @Test
    void authenticationFailureUsesProblemDetailsAndDoesNotEchoInternalReason() throws Exception {
        var request = request("/api/dashboard");
        var response = new MockHttpServletResponse();

        handler.commence(request, response, new InsufficientAuthenticationException("private security detail"));

        var body = json.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(body.path("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.path("detail").asText()).isEqualTo("Autenticação necessária.");
        assertThat(body.path("requestId").asText()).isEqualTo("test-request-id");
        assertThat(response.getContentAsString()).doesNotContain("private security detail");
    }

    @Test
    void accessDeniedUsesProblemDetailsWithForbiddenStatus() throws Exception {
        var request = request("/api/cash-transactions/adjustments");
        var response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("CSRF token missing"));

        var body = json.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body.path("status").asInt()).isEqualTo(403);
        assertThat(body.path("detail").asText()).isEqualTo("Acesso negado.");
        assertThat(response.getContentAsString()).doesNotContain("CSRF token missing");
    }

    private static MockHttpServletRequest request(String uri) {
        var request = new MockHttpServletRequest("POST", uri);
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, "test-request-id");
        return request;
    }
}
