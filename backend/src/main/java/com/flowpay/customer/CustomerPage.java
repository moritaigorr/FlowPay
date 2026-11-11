package com.flowpay.customer;

import java.util.List;

public record CustomerPage(List<CustomerView> items, int page, int size, long totalElements, long totalPages) {
    public CustomerPage {
        items = List.copyOf(items);
    }
}
