package com.example.expenses.service;

import com.example.expenses.dto.CategorySummary;
import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.DailyAllowance;
import com.example.expenses.dto.DailySpending;
import com.example.expenses.dto.DailyTotal;
import com.example.expenses.dto.MonthlyBalance;
import com.example.expenses.dto.MonthlySummary;
import com.example.expenses.dto.PaymentMethodTotal;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.model.Money;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.IncomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class SummaryService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final IncomeRepository incomeRepository;

    public SummaryService(ExpenseRepository expenseRepository, BudgetRepository budgetRepository,
                          IncomeRepository incomeRepository) {
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
        this.incomeRepository = incomeRepository;
    }

    public MonthlySummary summarize(Long userId, YearMonth month) {
        List<CategoryTotal> totals = expenseRepository.totalsByCategory(userId, month.atDay(1), month.atEndOfMonth());

        Map<Category, BigDecimal> limits = new EnumMap<>(Category.class);
        for (Budget budget : budgetRepository.findAllByOwnerIdOrderByCategoryAsc(userId)) {
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

    /**
     * What's left of all category budgets, divided by the days left in the month (today included).
     * Rounded to the cent with the app's money rules (Money: HALF_UP).
     * Spending in categories without a budget doesn't count. Empty when no budgets are set or the month is over.
     */
    public Optional<DailyAllowance> dailyAllowance(MonthlySummary summary, LocalDate today) {
        List<CategorySummary> budgeted = summary.categories().stream().filter(CategorySummary::hasBudget).toList();
        YearMonth month = summary.month();
        YearMonth current = YearMonth.from(today);
        if (budgeted.isEmpty() || month.isBefore(current)) {
            return Optional.empty();
        }
        int daysLeft = month.equals(current)
                ? month.lengthOfMonth() - today.getDayOfMonth() + 1
                : month.lengthOfMonth();
        BigDecimal remaining = budgeted.stream()
                .map(CategorySummary::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal perDay = Money.divide(remaining, daysLeft);
        return Optional.of(new DailyAllowance(remaining, daysLeft, perDay));
    }

    /** Spending per payment method for the month, with each one's share of the total. */
    public List<PaymentMethodTotal> paymentTotals(Long userId, YearMonth month) {
        List<PaymentMethodTotal> totals =
                expenseRepository.totalsByPaymentMethod(userId, month.atDay(1), month.atEndOfMonth());
        BigDecimal sum = totals.stream().map(PaymentMethodTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return totals.stream()
                .map(t -> new PaymentMethodTotal(t.method(), money(t.total()), percent(t.total(), sum)))
                .toList();
    }

    /** Income minus spending for the summary's month. */
    public MonthlyBalance balance(Long userId, MonthlySummary summary) {
        YearMonth month = summary.month();
        BigDecimal income = incomeRepository.totalBetween(userId, month.atDay(1), month.atEndOfMonth());
        income = money(income == null ? BigDecimal.ZERO : income);
        return new MonthlyBalance(income, summary.totalSpent(), income.subtract(summary.totalSpent()));
    }

    /** Spending for every day of the month, with zero for days without expenses (for the chart). */
    public DailySpending dailySpending(Long userId, YearMonth month) {
        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (DailyTotal total : expenseRepository.dailyTotals(userId, month.atDay(1), month.atEndOfMonth())) {
            byDate.put(total.date(), total.total());
        }
        List<DailyTotal> days = IntStream.rangeClosed(1, month.lengthOfMonth())
                .mapToObj(month::atDay)
                .map(date -> new DailyTotal(date, money(byDate.getOrDefault(date, BigDecimal.ZERO))))
                .toList();
        return new DailySpending(month, days);
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
        return Money.percent(part, whole);
    }

    private static BigDecimal money(BigDecimal value) {
        return Money.of(value);
    }
}
