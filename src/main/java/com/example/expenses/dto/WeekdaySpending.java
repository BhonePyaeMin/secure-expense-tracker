package com.example.expenses.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Spending on one day of the week.
 *
 * @param days       how many of these weekdays the month has had so far
 * @param average    total divided by days, so a weekday that came up five times isn't over-counted
 * @param barPercent average as a percentage of the highest weekday average, for the bar width
 * @param highest    the weekday with the highest average (none when nothing was spent)
 */
public record WeekdaySpending(DayOfWeek day, BigDecimal total, int days, BigDecimal average,
                              BigDecimal barPercent, boolean highest) {

    public String label() {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
