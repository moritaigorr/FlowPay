package com.flowpay.company;

import java.util.List;

public final class ContractValidationException extends RuntimeException {
    public record FieldError(String field, String code, String message) {}
    private final List<FieldError> fieldErrors;

    public ContractValidationException(String field, String code, String message) {
        super("Request validation failed.");
        fieldErrors = List.of(new FieldError(field, code, message));
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }
}
