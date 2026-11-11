package com.flowpay.payment.internal.domain;

import com.flowpay.company.ContractValidationException;
import com.flowpay.payment.PaymentStatus;
import com.flowpay.payment.SimulationScenario;

public final class PaymentRules {
    private PaymentRules() {}

    public record ProcessingResult(PaymentStatus status, String reasonCode) {}

    public static PaymentStatus transition(PaymentStatus current, PaymentStatus target) {
        if (current == null || target == null) {
            throw new ContractValidationException("status", "REQUIRED", "Current and target status are required.");
        }
        if (current != PaymentStatus.PENDING) {
            throw new PaymentFinalStateException(current);
        }
        if (target == PaymentStatus.PENDING) {
            throw new ContractValidationException("status", "INVALID_FORMAT", "Target status must be final.");
        }
        return target;
    }

    public static ProcessingResult process(PaymentStatus current, SimulationScenario scenario) {
        if (scenario == null) {
            throw new ContractValidationException("simulationScenario", "REQUIRED", "Simulation scenario is required.");
        }
        return switch (scenario) {
            case APPROVE -> new ProcessingResult(transition(current, PaymentStatus.APPROVED), null);
            case DECLINE -> new ProcessingResult(transition(current, PaymentStatus.REJECTED), "SIMULATED_DECLINE");
        };
    }
}
