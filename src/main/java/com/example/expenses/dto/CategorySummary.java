package com.example.expenses.dto;

import com.example.expenses.model.Category;

import java.math.BigDecimal;

/**
 * One row of the monthly summary.
 *
 * @param limit      the monthly budget, or null when the category has none
 * @param remaining  limit minus spent (negative when over), or null without a budget
 * @param barPercent spent as a percentage of the biggest category, for the bar width
 */
public record CategorySummary(Category category,
                              BigDecimal spent,
                              BigDecimal limit,
                              BigDecimal remaining,
                              boolean overBudget,
                              BigDecimal barPercent) {

    public boolean hasBudget() {
        return limit != null;
    }
}
