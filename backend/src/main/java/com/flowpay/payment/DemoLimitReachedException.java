package com.flowpay.payment;

public final class DemoLimitReachedException extends RuntimeException {
    public DemoLimitReachedException() { super("The demo payment limit has been reached."); }
}
