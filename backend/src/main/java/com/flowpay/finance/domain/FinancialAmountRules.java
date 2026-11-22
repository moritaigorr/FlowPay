package com.flowpay.finance.domain;

/** Pure amount rules shared by settlement and reversal use cases. */
public final class FinancialAmountRules {
    private FinancialAmountRules() {}

    public static long remaining(long originalAmountMinor, long allocatedAmountMinor) {
        if (originalAmountMinor <= 0 || allocatedAmountMinor < 0 || allocatedAmountMinor > originalAmountMinor) {
            throw new IllegalArgumentException("Allocated amount must be within the positive original amount.");
        }
        return originalAmountMinor - allocatedAmountMinor;
    }

    public static long requireAvailable(long requestedAmountMinor, long availableAmountMinor) {
        if (requestedAmountMinor <= 0 || availableAmountMinor < 0 || requestedAmountMinor > availableAmountMinor) {
            throw new IllegalArgumentException("Requested amount must be positive and no greater than the available amount.");
        }
        return requestedAmountMinor;
    }
}
