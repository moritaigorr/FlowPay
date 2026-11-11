package com.flowpay.customer;

public final class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException() { super("Customer was not found."); }
}
