package com.flowpay.payment.internal.domain;

import com.flowpay.company.ContractValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public final class PaymentAmount {
    private static final Pattern INPUT = Pattern.compile("(0|[1-9][0-9]{0,16})(\\.[0-9]{1,2})?");
    private final BigDecimal value;

    private PaymentAmount(BigDecimal value) {
        this.value = value;
    }

    public static PaymentAmount parse(String input) {
        if (input == null || input.isEmpty()) {
            throw new ContractValidationException("amount", "REQUIRED", "Amount is required.");
        }
        // Validate the wire grammar before BigDecimal, which also accepts signs and exponents.
        if (input.length() > 20 || !INPUT.matcher(input).matches()) {
            throw new ContractValidationException("amount", "INVALID_FORMAT",
                    "Amount must be a decimal string with at most 17 integer digits and two decimal places.");
        }
        var value = new BigDecimal(input);
        if (value.signum() <= 0) {
            throw new ContractValidationException("amount", "OUT_OF_RANGE", "Amount must be greater than zero.");
        }
        return new PaymentAmount(value.setScale(2, RoundingMode.UNNECESSARY));
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
