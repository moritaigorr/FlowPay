package com.flowpay.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentView(UUID id, UUID customerId, BigDecimal amount, String currency,
        PaymentStatus status, String description, SimulationScenario simulationScenario,
        Instant createdAt, Instant updatedAt) {}
