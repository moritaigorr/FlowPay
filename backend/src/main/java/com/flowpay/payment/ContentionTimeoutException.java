package com.flowpay.payment;

public final class ContentionTimeoutException extends RuntimeException {
    public ContentionTimeoutException() { super("Payment creation could not acquire its lock."); }
}
