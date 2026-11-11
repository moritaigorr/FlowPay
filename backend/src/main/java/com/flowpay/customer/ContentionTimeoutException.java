package com.flowpay.customer;

public final class ContentionTimeoutException extends RuntimeException {
    public ContentionTimeoutException() { super("The operation could not acquire its lock."); }
}
