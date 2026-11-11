package com.flowpay.customer;

import java.util.UUID;

public interface CustomerAccess {
    CustomerView requireOwned(UUID companyId, UUID customerId);
}
