package com.flowpay.identity;

public final class OperatorUnavailableException extends RuntimeException {
    public OperatorUnavailableException() {
        super("Operator is unavailable.");
    }
}
