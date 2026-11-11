package com.flowpay.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSummary(Instant from, Instant to, String currency,
        BigDecimal approvedVolume, long pendingCount, BigDecimal pendingAmount,
        long approvedCount, long rejectedCount, long totalPayments, String approvalRate) {}
