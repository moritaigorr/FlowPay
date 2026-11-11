package com.flowpay.payment;

public final class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() { super("The idempotency key was used for a different request."); }
}
