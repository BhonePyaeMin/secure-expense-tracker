package com.example.expenses.dto;

import com.example.expenses.model.Money;

import java.math.BigDecimal;

/** How an amount changed from one period to the next. */
public record Change(BigDecimal current, BigDecimal previous) {

    public BigDecimal difference() {
        return current.subtract(previous);
    }

    /** Change as a percentage of the previous amount, e.g. 12.5; null when there was nothing before. */
    public BigDecimal percent() {
        if (previous.signum() == 0) {
            return null;
        }
        return Money.percent(difference(), previous);
    }

    /** "+12.5%", "-50.0%", or "" when there's nothing to compare with. */
    public String percentText() {
        BigDecimal percent = percent();
        if (percent == null) {
            return "";
        }
        return (percent.signum() > 0 ? "+" : "") + percent.toPlainString() + "%";
    }

    /** Spending this period and none in the previous one. */
    public boolean isNew() {
        return previous.signum() == 0 && current.signum() > 0;
    }

    public boolean isUp() {
        return !isNew() && difference().signum() > 0;
    }

    public boolean isDown() {
        return difference().signum() < 0;
    }
}
