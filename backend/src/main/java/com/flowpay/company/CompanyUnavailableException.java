package com.flowpay.company;

public final class CompanyUnavailableException extends RuntimeException {
    public enum Reason { MISSING, EXPIRED_DEMO }
    private final Reason reason;

    public CompanyUnavailableException(Reason reason) {
        super("Company is unavailable.");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
