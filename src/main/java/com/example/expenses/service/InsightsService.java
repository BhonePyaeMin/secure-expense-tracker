package com.example.expenses.service;

import com.example.expenses.dto.CategoryComparison;
import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.Change;
import com.example.expenses.dto.MonthComparison;
import com.example.expenses.dto.SpendingPace;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.RecurringExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The numbers on the Insights page. All totals leave out the trash and are scoped to one user. */
@Service
@Transactional(readOnly = true)
public class InsightsService {

    private final ExpenseRepository expenseRepository;
    private final RecurringExpenseRepository recurringRepository;

    public InsightsService(ExpenseRepository expenseRepository, RecurringExpenseRepository recurringRepository) {
        this.expenseRepository = expenseRepository;
        this.recurringRepository = recurringRepository;
    }

    /** The five biggest expenses of the month. */
    public List<Expense> topExpenses(Long userId, YearMonth month) {
        return expenseRepository.findTop5ByOwnerIdAndDeletedAtIsNullAndDateBetweenOrderByAmountDescDateDescIdDesc(
                userId, month.atDay(1), month.atEndOfMonth());
    }

    /** Daily average and end-of-month projection; empty for a month that hasn't started. */
    public Optional<SpendingPace> pace(Long userId, YearMonth month, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (month.isAfter(current)) {
            return Optional.empty();
        }
        boolean ongoing = month.equals(current);
        LocalDate from = month.atDay(1);
        LocalDate to = ongoing ? today : month.atEndOfMonth();
        BigDecimal spent = orZero(expenseRepository.totalBetween(userId, from, to));
        BigDecimal fixed = orZero(expenseRepository.recurringTotalBetween(userId, from, to));
        BigDecimal upcoming = ongoing
                ? orZero(recurringRepository.upcomingTotal(userId, today, month.atEndOfMonth()))
                : BigDecimal.ZERO;
        return Optional.of(SpendingPace.calculate(month, today, spent, fixed, upcoming));
    }

    /**
     * Each category's spending in {@code month} next to the month before. While a month is still
     * running, both months are compared over the same days (1 to today), so a half-finished month
     * isn't measured against a whole one. If the previous month is shorter, it's compared in full.
     */
    public MonthComparison compareWithPreviousMonth(Long userId, YearMonth month, LocalDate today) {
        YearMonth previous = month.minusMonths(1);
        boolean ongoing = month.equals(YearMonth.from(today));
        LocalDate currentEnd = ongoing ? today : month.atEndOfMonth();
        LocalDate previousEnd = ongoing
                ? previous.atDay(Math.min(today.getDayOfMonth(), previous.lengthOfMonth()))
                : previous.atEndOfMonth();

        Map<Category, BigDecimal> current = byCategory(
                expenseRepository.totalsByCategory(userId, month.atDay(1), currentEnd));
        Map<Category, BigDecimal> before = byCategory(
                expenseRepository.totalsByCategory(userId, previous.atDay(1), previousEnd));

        Set<Category> categories = EnumSet.noneOf(Category.class);
        categories.addAll(current.keySet());
        categories.addAll(before.keySet());

        List<CategoryComparison> rows = categories.stream()
                .map(category -> new CategoryComparison(category, new Change(
                        current.getOrDefault(category, BigDecimal.ZERO),
                        before.getOrDefault(category, BigDecimal.ZERO))))
                .sorted(Comparator.comparing((CategoryComparison c) -> c.change().current()).reversed()
                        .thenComparing(c -> c.change().previous(), Comparator.reverseOrder())
                        .thenComparing(CategoryComparison::category))
                .toList();

        Change total = new Change(sum(current), sum(before));
        return new MonthComparison(month, previous, ongoing ? today.getDayOfMonth() : null, rows, total);
    }

    private static Map<Category, BigDecimal> byCategory(List<CategoryTotal> totals) {
        Map<Category, BigDecimal> map = new EnumMap<>(Category.class);
        for (CategoryTotal total : totals) {
            map.put(total.category(), money(total.total()));
        }
        return map;
    }

    private static BigDecimal sum(Map<Category, BigDecimal> amounts) {
        return money(amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
