package com.flowpay.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentFilter(PaymentStatus status, UUID customerId, Instant createdFrom,
        Instant createdTo, String query) {}
