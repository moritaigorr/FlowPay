package com.flowpay.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void publishesServerGeneratedIdAndAddsItToRequestLogContext() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/accounts");
        request.addHeader(RequestCorrelationFilter.RESPONSE_HEADER, "caller-controlled-id");
        var response = new MockHttpServletResponse();
        var observedMdcId = new AtomicReference<String>();
        MDC.put("request_id", "outer-context-id");

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                observedMdcId.set(MDC.get("request_id"));
                ((MockHttpServletResponse) servletResponse).setStatus(204);
            });

            var responseId = response.getHeader(RequestCorrelationFilter.RESPONSE_HEADER);
            assertThat(responseId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    .isNotEqualTo("caller-controlled-id");
            assertThat(observedMdcId.get()).isEqualTo(responseId);
            assertThat(MDC.get("request_id")).isEqualTo("outer-context-id");
            assertThat(response.getStatus()).isEqualTo(204);
        } finally {
            MDC.remove("request_id");
        }
    }

    @Test
    void clearsRequestIdFromMdcWhenRequestFails() {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response,
                (servletRequest, servletResponse) -> { throw new jakarta.servlet.ServletException("request failed"); }))
                .isInstanceOf(jakarta.servlet.ServletException.class);

        assertThat(response.getHeader(RequestCorrelationFilter.RESPONSE_HEADER)).isNotBlank();
        assertThat(MDC.get("request_id")).isNull();
    }
}
