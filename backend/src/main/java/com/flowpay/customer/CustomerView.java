package com.flowpay.customer;

import java.time.Instant;
import java.util.UUID;

public record CustomerView(UUID id, String name, String email, Instant createdAt, Instant updatedAt) {}
