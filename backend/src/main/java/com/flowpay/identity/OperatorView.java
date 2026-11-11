package com.flowpay.identity;

import java.time.Instant;
import java.util.UUID;

public record OperatorView(UUID id, UUID companyId, String email, Instant createdAt) {}
