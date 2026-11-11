package com.flowpay.config.demo;

public final class DemoCapacityReachedException extends RuntimeException {
    public DemoCapacityReachedException() {
        super("The demo service is at its active capacity. Please try again later.");
    }
}
