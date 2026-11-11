package com.flowpay.payment;

import java.time.Instant;
import java.util.UUID;

public interface PaymentSummaryReader {
    PaymentSummary summarize(UUID companyId, Instant from, Instant to);
}
