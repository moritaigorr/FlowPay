package com.flowpay.payment;

import java.util.List;
import java.util.UUID;

public interface PaymentAccess {
    PaymentView requireOwned(UUID companyId, UUID paymentId);
    List<PaymentEventView> events(UUID companyId, UUID paymentId);
}
