package com.flowpay.payment;

import java.util.UUID;
import java.time.Instant;

public interface PaymentOperations {
    CreatedPayment create(UUID companyId, UUID actorUserId, String idempotencyKey, NewPayment command);
    PaymentView process(UUID companyId, UUID actorUserId, UUID paymentId);
    PaymentView cancel(UUID companyId, UUID actorUserId, UUID paymentId);
    void deleteExpiredDemoPayments(UUID companyId, Instant cutoff);
}
