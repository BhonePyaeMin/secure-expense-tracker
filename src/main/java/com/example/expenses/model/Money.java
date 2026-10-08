package com.example.expenses.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one place for money rounding: 2 decimal places, HALF_UP (0.005 becomes 0.01). Every total
 * and division of an amount goes through here, so the same input always rounds the same way.
 * Percentages use one decimal place with the same rounding mode.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final int PERCENT_SCALE = 1;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    /** The amount rounded to cents, e.g. 1.005 becomes 1.01 and 2.5 becomes 2.50. */
    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    /** Sum of the amounts, in cents (0.00 when there are none). */
    public static BigDecimal total(Iterable<BigDecimal> amounts) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            sum = sum.add(amount);
        }
        return of(sum);
    }

    /** amount / divisor in cents, e.g. 10.00 / 3 = 3.33 and 20.00 / 3 = 6.67. */
    public static BigDecimal divide(BigDecimal amount, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new IllegalArgumentException("Can't divide an amount by zero");
        }
        return amount.divide(divisor, SCALE, ROUNDING);
    }

    public static BigDecimal divide(BigDecimal amount, long divisor) {
        return divide(amount, BigDecimal.valueOf(divisor));
    }

    /** part as a percentage of whole with one decimal, e.g. 1 of 3 = 33.3; 0 when whole is zero. */
    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, PERCENT_SCALE, ROUNDING);
    }
}
