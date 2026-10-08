package com.example.expenses.dto;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Spending per category in one month compared with the month before.
 *
 * @param throughDay for the current month, the day it runs to (today); both months are then compared
 *                   over the same days, e.g. 1-8 October against 1-8 September. Null for finished months.
 */
public record MonthComparison(YearMonth month,
                              YearMonth previousMonth,
                              Integer throughDay,
                              List<CategoryComparison> categories,
                              Change total) {

    public boolean isPartial() {
        return throughDay != null;
    }

    /** The category whose spending went up the most in money terms, if any went up. */
    public Optional<CategoryComparison> biggestIncrease() {
        return categories.stream()
                .filter(c -> c.change().difference().signum() > 0)
                .max(Comparator.comparing(c -> c.change().difference()));
    }
}
