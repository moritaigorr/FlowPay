package com.flowpay.config.web;

import java.io.IOException;
import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiProblems {
    private final JsonMapper mapper;

    public ApiProblems(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public ProblemDetail create(HttpServletRequest request, HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("requestId", request.getAttribute(RequestIdFilter.ATTRIBUTE));
        return problem;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), create(request, status, code, detail));
    }
}
