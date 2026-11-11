package com.flowpay.payment.internal.domain;

import com.flowpay.company.ContractValidationException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentAmountTest {
    @Test
    void acceptsExactAmountsAndCanonicalizesToTwoDecimalPlaces() {
        var examples = Map.of(
                "150.00", "150.00", "1", "1.00", "1.0", "1.00", "1.00", "1.00",
                "0.01", "0.01", "0.1", "0.10", "99999999999999999.99", "99999999999999999.99",
                "99999999999999999", "99999999999999999.00");
        examples.forEach((input, canonical) -> {
            var amount = PaymentAmount.parse(input);
            assertEquals(new BigDecimal(canonical), amount.value(), input);
            assertEquals(2, amount.value().scale(), input);
            assertEquals(canonical, amount.toString(), input);
        });
    }

    @Test
    void rejectsMissingAmountWithSafeFieldMetadata() {
        for (String input : new String[] {null, ""}) {
            assertAmountError(input, "REQUIRED");
        }
    }

    @Test
    void rejectsZeroRatherThanAllowingANonpositivePayment() {
        for (String input : new String[] {"0", "0.0", "0.00"}) {
            assertAmountError(input, "OUT_OF_RANGE");
        }
    }

    @Test
    void rejectsUnsupportedLexicalFormsRatherThanNormalizingOrRounding() {
        for (String input : new String[] {
                "-1.00", "-0.00", "+1.00", "1.001", "1.000", "0.001", "1e2", "1E+2", "1e-2",
                " 1.00", "1.00 ", "\t1.00", "1.00\n", "\n1.00", "1.00\r\n", " ", "\u00a01.00",
                "01", "00.01", "01.00", ".01", "1.", "1,00", "1_000.00", "1,000.00", "1 000.00",
                "NaN", "Infinity", "null", "\"1.00\"", "١.٠٠", "１.００"
        }) {
            assertAmountError(input, "INVALID_FORMAT");
        }
    }

    @Test
    void rejectsValuesBeyondTheDatabasePrecision() {
        for (String input : new String[] {
                "100000000000000000", "100000000000000000.00", "999999999999999999.99", "9".repeat(1000)
        }) {
            assertThrows(ContractValidationException.class, () -> PaymentAmount.parse(input), input);
        }
    }

    private void assertAmountError(String input, String code) {
        var exception = assertThrows(ContractValidationException.class, () -> PaymentAmount.parse(input), input);
        assertEquals(1, exception.fieldErrors().size());
        assertEquals("amount", exception.fieldErrors().getFirst().field());
        assertEquals(code, exception.fieldErrors().getFirst().code());
    }
}
