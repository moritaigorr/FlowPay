package com.flowpay.payment;

import java.util.List;

public record PaymentPage(List<PaymentView> items, int page, int size, long totalElements, long totalPages) {
    public PaymentPage {
        items = List.copyOf(items);
    }
}
