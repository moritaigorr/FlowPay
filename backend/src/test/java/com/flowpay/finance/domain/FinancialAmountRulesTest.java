package com.flowpay.finance.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FinancialAmountRulesTest {
    @Test
    void leavesTheCorrectBalanceAfterAPartialSettlement() {
        assertEquals(3_000, FinancialAmountRules.remaining(10_000, 7_000));
    }

    @Test
    void rejectsSettlementLargerThanTheAvailableBalance() {
        assertThrows(IllegalArgumentException.class, () -> FinancialAmountRules.requireAvailable(7_000, 3_000));
    }

    @Test
    void allowsAFullSettlementAtTheExactBoundary() {
        assertEquals(10_000, FinancialAmountRules.requireAvailable(10_000, 10_000));
        assertEquals(0, FinancialAmountRules.remaining(10_000, 10_000));
    }

    @Test
    void rejectsAnAmountOutsideTheOriginalTitleValue() {
        assertThrows(IllegalArgumentException.class, () -> FinancialAmountRules.remaining(10_000, 10_001));
    }

    @Test
    void rejectsZeroAndNegativeAllocations() {
        assertThrows(IllegalArgumentException.class, () -> FinancialAmountRules.requireAvailable(0, 100));
        assertThrows(IllegalArgumentException.class, () -> FinancialAmountRules.requireAvailable(-1, 100));
    }
}
