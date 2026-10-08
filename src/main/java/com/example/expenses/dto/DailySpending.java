package com.example.expenses.dto;

import com.example.expenses.model.Money;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spending for every day of a month (days without spending are zero), for the summary chart.
 */
public record DailySpending(YearMonth month, List<DailyTotal> days) {

    /** One value per day, e.g. "0.00,65.00,0.00,...", read by summary-chart.js from a data attribute. */
    public String amountsCsv() {
        return days.stream().map(day -> day.total().toPlainString()).collect(Collectors.joining(","));
    }

    public boolean hasSpending() {
        return days.stream().anyMatch(day -> day.total().signum() > 0);
    }

    /** The table view under the chart lists only days with spending. */
    public List<DailyTotal> daysWithSpending() {
        return days.stream().filter(day -> day.total().signum() > 0).toList();
    }

    public BigDecimal total() {
        return Money.total(days.stream().map(DailyTotal::total).toList());
    }
}
