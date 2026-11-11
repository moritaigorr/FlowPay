package com.flowpay.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentEventView(UUID id, UUID paymentId, long sequence,
        PaymentStatus previousStatus, PaymentStatus newStatus, String reasonCode,
        UUID actorUserId, Instant createdAt) {}
