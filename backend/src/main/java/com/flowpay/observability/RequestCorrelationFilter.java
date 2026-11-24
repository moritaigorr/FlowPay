package com.flowpay.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String RESPONSE_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";
    private static final String MDC_KEY = "request_id";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        var startedAt = System.nanoTime();
        var previousRequestId = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(RESPONSE_HEADER, requestId);
        var status = response.getStatus();
        var outcome = "completed";
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } catch (IOException | ServletException | RuntimeException failure) {
            outcome = "exception";
            status = response.isCommitted() ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            throw failure;
        } finally {
            var durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            try {
                LOGGER.info("http_request_completed method={} status={} outcome={} duration_ms={}",
                        request.getMethod(), status, outcome, durationMillis);
            } finally {
                if (previousRequestId == null) {
                    MDC.remove(MDC_KEY);
                } else {
                    MDC.put(MDC_KEY, previousRequestId);
                }
            }
        }
    }
}
