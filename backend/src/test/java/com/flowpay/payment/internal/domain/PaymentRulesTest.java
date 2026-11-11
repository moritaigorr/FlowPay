package com.flowpay.payment.internal.domain;

import com.flowpay.company.ContractValidationException;
import com.flowpay.payment.PaymentStatus;
import com.flowpay.payment.SimulationScenario;
import org.junit.jupiter.api.Test;

import static com.flowpay.payment.PaymentStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentRulesTest {
    @Test
    void allowsPendingToEachFinalStatus() {
        for (var target : new PaymentStatus[] {APPROVED, REJECTED, CANCELLED}) {
            assertEquals(target, PaymentRules.transition(PENDING, target));
        }
    }

    @Test
    void rejectsPendingToPending() {
        var exception = assertThrows(ContractValidationException.class, () -> PaymentRules.transition(PENDING, PENDING));
        assertEquals(1, exception.fieldErrors().size());
        assertEquals("status", exception.fieldErrors().getFirst().field());
        assertEquals("INVALID_FORMAT", exception.fieldErrors().getFirst().code());
    }

    @Test
    void rejectsEveryTransitionFromEveryFinalStatusIncludingSameStatus() {
        for (var current : new PaymentStatus[] {APPROVED, REJECTED, CANCELLED}) {
            for (var target : PaymentStatus.values()) {
                var exception = assertThrows(PaymentFinalStateException.class,
                        () -> PaymentRules.transition(current, target), current + " -> " + target);
                assertEquals(current, exception.status());
            }
        }
    }

    @Test
    void processingIsDeterministicAndIncludesOnlyTheDeclineReason() {
        for (int attempt = 0; attempt < 3; attempt++) {
            var approved = PaymentRules.process(PENDING, SimulationScenario.APPROVE);
            assertEquals(APPROVED, approved.status());
            assertNull(approved.reasonCode());
            var rejected = PaymentRules.process(PENDING, SimulationScenario.DECLINE);
            assertEquals(REJECTED, rejected.status());
            assertEquals("SIMULATED_DECLINE", rejected.reasonCode());
        }
    }

    @Test
    void neitherScenarioCanProcessAFinalPayment() {
        for (var current : new PaymentStatus[] {APPROVED, REJECTED, CANCELLED}) {
            for (var scenario : SimulationScenario.values()) {
                var exception = assertThrows(PaymentFinalStateException.class,
                        () -> PaymentRules.process(current, scenario), current + " / " + scenario);
                assertEquals(current, exception.status());
            }
        }
    }

    @Test
    void rejectsNullRuleInputsWithValidationErrors() {
        assertThrows(ContractValidationException.class, () -> PaymentRules.transition(null, APPROVED));
        assertThrows(ContractValidationException.class, () -> PaymentRules.transition(PENDING, null));
        assertThrows(ContractValidationException.class, () -> PaymentRules.transition(null, null));
        assertThrows(ContractValidationException.class, () -> PaymentRules.process(null, SimulationScenario.APPROVE));
        assertThrows(ContractValidationException.class, () -> PaymentRules.process(PENDING, null));
        assertThrows(ContractValidationException.class, () -> PaymentRules.process(null, null));
    }
}
