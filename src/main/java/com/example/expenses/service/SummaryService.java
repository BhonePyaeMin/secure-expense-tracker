package com.example.expenses.service;

import com.example.expenses.dto.CategorySummary;
import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.MonthlySummary;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SummaryService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;

    public SummaryService(ExpenseRepository expenseRepository, BudgetRepository budgetRepository) {
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
    }

    public MonthlySummary summarize(YearMonth month) {
        List<CategoryTotal> totals = expenseRepository.totalsByCategory(month.atDay(1), month.atEndOfMonth());

        Map<Category, BigDecimal> limits = new EnumMap<>(Category.class);
        for (Budget budget : budgetRepository.findAll()) {
            limits.put(budget.getCategory(), budget.getMonthlyLimit());
        }

        // Show every category that has spending or a budget (a budget with no spending yet still matters)
        Map<Category, BigDecimal> spentByCategory = new EnumMap<>(Category.class);
        for (CategoryTotal total : totals) {
            spentByCategory.put(total.category(), total.total());
        }
        for (Category category : limits.keySet()) {
            spentByCategory.putIfAbsent(category, BigDecimal.ZERO);
        }

        BigDecimal totalSpent = money(spentByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal biggest = spentByCategory.values().stream()
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        List<CategorySummary> rows = spentByCategory.entrySet().stream()
                .map(entry -> row(entry.getKey(), money(entry.getValue()), limits.get(entry.getKey()), biggest))
                .sorted(Comparator.comparing(CategorySummary::spent).reversed()
                        .thenComparing(CategorySummary::category))
                .toList();

        return new MonthlySummary(month, totalSpent, rows);
    }

    private static CategorySummary row(Category category, BigDecimal spent, BigDecimal limit, BigDecimal biggest) {
        BigDecimal remaining = limit == null ? null : limit.subtract(spent);
        boolean overBudget = limit != null && spent.compareTo(limit) > 0;
        return new CategorySummary(category, spent, limit, remaining, overBudget, percent(spent, biggest));
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
