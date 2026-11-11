package com.flowpay.payment;

public final class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException() {
        super("Payment was not found.");
    }
}
