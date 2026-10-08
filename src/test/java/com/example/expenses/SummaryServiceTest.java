package com.example.expenses;

import com.example.expenses.dto.CategorySummary;
import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.DailyAllowance;
import com.example.expenses.dto.DailySpending;
import com.example.expenses.dto.DailyTotal;
import com.example.expenses.dto.MonthlyBalance;
import com.example.expenses.dto.MonthlySummary;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.IncomeRepository;
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
    private final IncomeRepository incomeRepository = mock(IncomeRepository.class);
    private final SummaryService summaryService =
            new SummaryService(expenseRepository, budgetRepository, incomeRepository);

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

    @Test
    void dailySpendingHasEveryDayOfTheMonthWithZerosForQuietDays() {
        when(expenseRepository.dailyTotals(USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(
                        new DailyTotal(LocalDate.of(2026, 10, 2), new BigDecimal("65.5")),
                        new DailyTotal(LocalDate.of(2026, 10, 31), new BigDecimal("10"))));

        DailySpending daily = summaryService.dailySpending(USER_ID, OCTOBER);

        assertThat(daily.days()).hasSize(31);
        assertThat(daily.amountsCsv()).startsWith("0.00,65.50,0.00,").endsWith(",10.00");
        assertThat(daily.daysWithSpending()).extracting(DailyTotal::date)
                .containsExactly(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 31));
        assertThat(daily.total()).isEqualByComparingTo("75.50");
    }

    @Test
    void dailySpendingForAMonthWithNoExpensesHasNothingToChart() {
        when(expenseRepository.dailyTotals(USER_ID, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .thenReturn(List.of());

        DailySpending daily = summaryService.dailySpending(USER_ID, YearMonth.of(2026, 2));

        assertThat(daily.days()).hasSize(28);
        assertThat(daily.hasSpending()).isFalse();
    }

    @Test
    void balanceIsIncomeMinusSpending() {
        givenTotals(new CategoryTotal(Category.FOOD, new BigDecimal("1246.50")));
        when(incomeRepository.totalBetween(USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(new BigDecimal("8000"));

        MonthlyBalance balance = summaryService.balance(USER_ID, summaryService.summarize(USER_ID, OCTOBER));

        assertThat(balance.income()).isEqualByComparingTo("8000.00");
        assertThat(balance.spent()).isEqualByComparingTo("1246.50");
        assertThat(balance.balance()).isEqualByComparingTo("6753.50");
        assertThat(balance.isNegative()).isFalse();
    }

    @Test
    void noIncomeMeansANegativeBalance() {
        givenTotals(new CategoryTotal(Category.FOOD, new BigDecimal("100")));

        MonthlyBalance balance = summaryService.balance(USER_ID, summaryService.summarize(USER_ID, OCTOBER));

        assertThat(balance.income()).isEqualByComparingTo("0");
        assertThat(balance.balance()).isEqualByComparingTo("-100");
        assertThat(balance.isNegative()).isTrue();
    }

    @Test
    void paymentTotalsGetTheirShareOfTheMonth() {
        when(expenseRepository.totalsByPaymentMethod(USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(
                        new com.example.expenses.dto.PaymentMethodTotal(com.example.expenses.model.PaymentMethod.PROMPTPAY, new BigDecimal("750")),
                        new com.example.expenses.dto.PaymentMethodTotal(null, new BigDecimal("250"))));

        var totals = summaryService.paymentTotals(USER_ID, OCTOBER);

        assertThat(totals).extracting(t -> t.label(), t -> t.percent().toPlainString())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PromptPay", "75.0"),
                        org.assertj.core.groups.Tuple.tuple("Not set", "25.0"));
    }

    // --- Daily allowance: remaining budget / days left (today included) ---

    @Test
    void allowanceSplitsWhatIsLeftOverTheDaysLeft() {
        MonthlySummary summary = summaryOf(budgetRow(Category.FOOD, "1000", "3000"),
                budgetRow(Category.TRANSPORT, "500", "1000"));

        DailyAllowance allowance = summaryService.dailyAllowance(summary, LocalDate.of(2026, 10, 22)).orElseThrow();

        assertThat(allowance.remaining()).isEqualByComparingTo("2500");
        assertThat(allowance.daysLeft()).isEqualTo(10); // 22nd to 31st
        assertThat(allowance.perDay()).isEqualByComparingTo("250.00");
        assertThat(allowance.isNegative()).isFalse();
    }

    @Test
    void overspendingMakesTheAllowanceNegative() {
        MonthlySummary summary = summaryOf(budgetRow(Category.FOOD, "3500", "3000"));

        DailyAllowance allowance = summaryService.dailyAllowance(summary, LocalDate.of(2026, 10, 30)).orElseThrow();

        assertThat(allowance.daysLeft()).isEqualTo(2);
        assertThat(allowance.perDay()).isEqualByComparingTo("-250.00");
        assertThat(allowance.isNegative()).isTrue();
    }

    @Test
    void oneCategoryOverIsOffsetByTheOthers() {
        MonthlySummary summary = summaryOf(budgetRow(Category.FOOD, "3200", "3000"),
                budgetRow(Category.FUN, "0", "1000"));

        DailyAllowance allowance = summaryService.dailyAllowance(summary, LocalDate.of(2026, 10, 31)).orElseThrow();

        assertThat(allowance.remaining()).isEqualByComparingTo("800");
        assertThat(allowance.daysLeft()).isEqualTo(1);
        assertThat(allowance.perDay()).isEqualByComparingTo("800.00");
    }

    @Test
    void exactlyAtTheLimitIsZeroAndNotNegative() {
        MonthlySummary summary = summaryOf(budgetRow(Category.FOOD, "3000", "3000"));

        DailyAllowance allowance = summaryService.dailyAllowance(summary, LocalDate.of(2026, 10, 8)).orElseThrow();

        assertThat(allowance.perDay()).isEqualByComparingTo("0");
        assertThat(allowance.isNegative()).isFalse();
    }

    @Test
    void roundsDownSoPositiveNeverOverstatesAndNegativeShowsTheFullOverspend() {
        LocalDate threeDaysLeft = LocalDate.of(2026, 10, 29);

        assertThat(summaryService.dailyAllowance(summaryOf(budgetRow(Category.FOOD, "2900", "3000")), threeDaysLeft)
                .orElseThrow().perDay()).isEqualByComparingTo("33.33");
        assertThat(summaryService.dailyAllowance(summaryOf(budgetRow(Category.FOOD, "3100", "3000")), threeDaysLeft)
                .orElseThrow().perDay()).isEqualByComparingTo("-33.34");
    }

    @Test
    void spendingWithoutABudgetDoesNotReduceTheAllowance() {
        MonthlySummary summary = summaryOf(budgetRow(Category.FOOD, "0", "3100"),
                new CategorySummary(Category.RENT, new BigDecimal("4500"), null, null, false, BigDecimal.ZERO));

        DailyAllowance allowance = summaryService.dailyAllowance(summary, LocalDate.of(2026, 10, 1)).orElseThrow();

        assertThat(allowance.daysLeft()).isEqualTo(31);
        assertThat(allowance.perDay()).isEqualByComparingTo("100.00");
    }

    @Test
    void aFutureMonthUsesAllItsDays() {
        MonthlySummary november = new MonthlySummary(YearMonth.of(2026, 11), BigDecimal.ZERO,
                List.of(budgetRow(Category.FOOD, "0", "3000")));

        DailyAllowance allowance = summaryService.dailyAllowance(november, LocalDate.of(2026, 10, 8)).orElseThrow();

        assertThat(allowance.daysLeft()).isEqualTo(30);
        assertThat(allowance.perDay()).isEqualByComparingTo("100.00");
    }

    @Test
    void noAllowanceWithoutBudgetsOrForAMonthThatIsOver() {
        assertThat(summaryService.dailyAllowance(summaryOf(), LocalDate.of(2026, 10, 8))).isEmpty();
        assertThat(summaryService.dailyAllowance(summaryOf(budgetRow(Category.FOOD, "10", "3000")),
                LocalDate.of(2026, 11, 1))).isEmpty();
    }

    private static MonthlySummary summaryOf(CategorySummary... rows) {
        BigDecimal total = java.util.Arrays.stream(rows).map(CategorySummary::spent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MonthlySummary(OCTOBER, total, List.of(rows));
    }

    private static CategorySummary budgetRow(Category category, String spent, String limit) {
        BigDecimal spentAmount = new BigDecimal(spent);
        BigDecimal limitAmount = new BigDecimal(limit);
        return new CategorySummary(category, spentAmount, limitAmount, limitAmount.subtract(spentAmount),
                spentAmount.compareTo(limitAmount) > 0, BigDecimal.ZERO);
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
