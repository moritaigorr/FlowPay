package com.flowpay.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record NewPayment(UUID customerId, BigDecimal amount, String currency,
        String description, SimulationScenario simulationScenario) {}
