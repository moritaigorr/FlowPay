package com.flowpay.identity;

import java.util.UUID;

public record NewOperator(UUID companyId, String email, String passwordHash, boolean demo) {}
