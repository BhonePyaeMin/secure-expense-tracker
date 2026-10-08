package com.example.expenses.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** Spending for one month, with the per-category rows sorted biggest first. */
public record MonthlySummary(YearMonth month, BigDecimal totalSpent, List<CategorySummary> categories) {

    public long overBudgetCount() {
        return categories.stream().filter(CategorySummary::overBudget).count();
    }
}
