package com.example.expenses.dto;

import java.math.BigDecimal;

/**
 * How much can still be spent per day this month without going over budget.
 *
 * @param remaining what's left of all category budgets this month (negative when over)
 * @param daysLeft  days left in the month, counting today
 * @param perDay    remaining divided by daysLeft, rounded to the cent (Money)
 */
public record DailyAllowance(BigDecimal remaining, int daysLeft, BigDecimal perDay) {

    public boolean isNegative() {
        return remaining.signum() < 0;
    }
}
