package com.example.expenses;

import com.example.expenses.dto.CategorySummary;
import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.MonthlySummary;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.service.SummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SummaryServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final Long USER_ID = 1L;

    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final SummaryService summaryService = new SummaryService(expenseRepository, budgetRepository);

    @BeforeEach
    void noBudgetsByDefault() {
        when(budgetRepository.findAllByOwnerIdOrderByCategoryAsc(USER_ID)).thenReturn(List.of());
    }

    @Test
    void noExpensesGivesZeroTotalAndNoRows() {
        givenTotals();

        MonthlySummary summary = summaryService.summarize(USER_ID, OCTOBER);

        assertThat(summary.totalSpent()).isEqualByComparingTo("0");
        assertThat(summary.categories()).isEmpty();
        assertThat(summary.overBudgetCount()).isZero();
    }

    @Test
    void budgetWithNoExpensesShowsFullAmountRemaining() {
        givenTotals();
        givenBudgets(new Budget(null, Category.FOOD, new BigDecimal("3000.00")));

        CategorySummary food = onlyRow(summaryService.summarize(USER_ID, OCTOBER));

        assertThat(food.spent()).isEqualByComparingTo("0");
        assertThat(food.remaining()).isEqualByComparingTo("3000.00");
        assertThat(food.overBudget()).isFalse();
        assertThat(food.barPercent()).isEqualByComparingTo("0");
    }

    @Test
    void spendingExactlyAtTheLimitIsNotOverBudget() {
        givenTotals(new CategoryTotal(Category.FOOD, new BigDecimal("3000.00")));
        givenBudgets(new Budget(null, Category.FOOD, new BigDecimal("3000.00")));

        CategorySummary food = onlyRow(summaryService.summarize(USER_ID, OCTOBER));

        assertThat(food.remaining()).isEqualByComparingTo("0");
        assertThat(food.overBudget()).isFalse();
    }

    @Test
    void spendingOverTheLimitIsFlaggedWithNegativeRemaining() {
        givenTotals(new CategoryTotal(Category.FOOD, new BigDecimal("3000.01")));
        givenBudgets(new Budget(null, Category.FOOD, new BigDecimal("3000.00")));

        MonthlySummary summary = summaryService.summarize(USER_ID, OCTOBER);
        CategorySummary food = onlyRow(summary);

        assertThat(food.remaining()).isEqualByComparingTo("-0.01");
        assertThat(food.overBudget()).isTrue();
        assertThat(summary.overBudgetCount()).isEqualTo(1);
    }

    @Test
    void categoriesWithoutBudgetHaveNoRemainingAndAreNeverOver() {
        givenTotals(new CategoryTotal(Category.FUN, new BigDecimal("999999.00")));

        CategorySummary fun = onlyRow(summaryService.summarize(USER_ID, OCTOBER));

        assertThat(fun.hasBudget()).isFalse();
        assertThat(fun.remaining()).isNull();
        assertThat(fun.overBudget()).isFalse();
    }

    @Test
    void totalsAddUpAndRowsAreSortedBiggestFirstWithProportionalBars() {
        givenTotals(
                new CategoryTotal(Category.FOOD, new BigDecimal("50.00")),
                new CategoryTotal(Category.RENT, new BigDecimal("200.00")),
                new CategoryTotal(Category.TRANSPORT, new BigDecimal("12.34")));

        MonthlySummary summary = summaryService.summarize(USER_ID, OCTOBER);

        assertThat(summary.totalSpent()).isEqualByComparingTo("262.34");
        assertThat(summary.categories()).extracting(CategorySummary::category)
                .containsExactly(Category.RENT, Category.FOOD, Category.TRANSPORT);
        assertThat(summary.categories()).extracting(CategorySummary::barPercent)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100.0"), new BigDecimal("25.0"), new BigDecimal("6.2"));
    }

    private void givenTotals(CategoryTotal... totals) {
        when(expenseRepository.totalsByCategory(USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(totals));
    }

    private void givenBudgets(Budget... budgets) {
        when(budgetRepository.findAllByOwnerIdOrderByCategoryAsc(USER_ID)).thenReturn(List.of(budgets));
    }

    private static CategorySummary onlyRow(MonthlySummary summary) {
        assertThat(summary.categories()).hasSize(1);
        return summary.categories().get(0);
    }
}
