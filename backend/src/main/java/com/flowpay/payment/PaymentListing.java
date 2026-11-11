package com.flowpay.payment;

import java.util.UUID;

public interface PaymentListing {
    PaymentPage list(UUID companyId, int page, int size, PaymentFilter filter);
}
