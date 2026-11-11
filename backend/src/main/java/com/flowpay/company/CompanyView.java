package com.flowpay.company;

import java.time.Instant;
import java.util.UUID;

public record CompanyView(UUID id, String name, Instant createdAt, boolean demo, Instant expiresAt) {}
