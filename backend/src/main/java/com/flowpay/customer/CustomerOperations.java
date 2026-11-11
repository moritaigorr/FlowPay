package com.flowpay.customer;

import java.util.UUID;
import java.time.Instant;

public interface CustomerOperations {
    CustomerView create(UUID companyId, NewCustomer command);
    void deleteExpiredDemoCustomers(UUID companyId, Instant cutoff);
}
