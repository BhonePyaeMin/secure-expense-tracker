package com.example.expenses.dto;

import java.math.BigDecimal;

/** Income minus expenses for one month. */
public record MonthlyBalance(BigDecimal income, BigDecimal spent, BigDecimal balance) {

    public boolean isNegative() {
        return balance.signum() < 0;
    }
}
