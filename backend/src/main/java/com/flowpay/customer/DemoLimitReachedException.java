package com.flowpay.customer;

public final class DemoLimitReachedException extends RuntimeException {
    public DemoLimitReachedException() { super("The demo customer limit has been reached."); }
}
