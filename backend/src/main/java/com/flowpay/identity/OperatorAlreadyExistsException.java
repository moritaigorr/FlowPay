package com.flowpay.identity;

public final class OperatorAlreadyExistsException extends RuntimeException {
    public OperatorAlreadyExistsException() {
        super("Operator provisioning refused.");
    }
}
