package com.flowpay.customer;

import java.util.UUID;

public interface CustomerListing {
    CustomerPage list(UUID companyId, int page, int size, String query);
}
