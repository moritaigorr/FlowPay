package com.flowpay.payment;

public final class PaymentFinalStateException extends RuntimeException {
    private final PaymentStatus status;

    public PaymentFinalStateException(PaymentStatus status) {
        super("A final payment cannot change status.");
        this.status = status;
    }

    public PaymentStatus status() {
        return status;
    }
}
