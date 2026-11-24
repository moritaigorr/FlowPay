package com.flowpay.api;

import java.net.URI;
import java.util.List;

/** RFC 9457 problem details with FlowPay request correlation and field errors. */
public record ApiProblem(URI type, String title, int status, String detail, String instance,
                         String requestId, List<FieldViolation> errors) {
    public record FieldViolation(String field, String code, String message) {}
}
