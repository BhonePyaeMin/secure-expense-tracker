package com.example.expenses;

import com.example.expenses.dto.CategoryComparison;
import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.Change;
import com.example.expenses.dto.MonthComparison;
import com.example.expenses.dto.SpendingPace;
import com.example.expenses.model.Category;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.RecurringExpenseRepository;
import com.example.expenses.service.InsightsService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightsServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final RecurringExpenseRepository recurringRepository = mock(RecurringExpenseRepository.class);
    private final InsightsService insightsService = new InsightsService(expenseRepository, recurringRepository);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    // --- Month-over-month comparison ---

    @Test
    void finishedMonthIsComparedWithTheWholePreviousMonth() {
        givenTotals("2026-09-01", "2026-09-30",
                total(Category.FOOD, "1100"), total(Category.TRANSPORT, "200"), total(Category.FUN, "300"));
        givenTotals("2026-08-01", "2026-08-31",
                total(Category.FOOD, "1000"), total(Category.TRANSPORT, "400"), total(Category.HEALTH, "150"));

        MonthComparison comparison = insightsService.compareWithPreviousMonth(USER_ID, YearMonth.of(2026, 9), TODAY);

        assertThat(comparison.isPartial()).isFalse();
        assertThat(comparison.categories())
                .extracting(CategoryComparison::category, c -> c.change().difference().toPlainString(),
                        c -> c.change().percentText())
                .containsExactly(
                        tuple(Category.FOOD, "100.00", "+10.0%"),
                        tuple(Category.FUN, "300.00", ""),          // new: nothing to compare with
                        tuple(Category.TRANSPORT, "-200.00", "-50.0%"),
                        tuple(Category.HEALTH, "-150.00", "-100.0%"));
        assertThat(comparison.total().current()).isEqualByComparingTo("1600");
        assertThat(comparison.total().previous()).isEqualByComparingTo("1550");
        assertThat(comparison.total().percentText()).isEqualTo("+3.2%"); // 50 / 1550 = 3.23%
        assertThat(comparison.biggestIncrease()).get().extracting(CategoryComparison::category).isEqualTo(Category.FUN);
    }

    @Test
    void currentMonthIsComparedOverTheSameDaysOfLastMonth() {
        givenTotals("2026-10-01", "2026-10-08", total(Category.FOOD, "400"));
        givenTotals("2026-09-01", "2026-09-08", total(Category.FOOD, "500"));

        MonthComparison comparison = insightsService.compareWithPreviousMonth(USER_ID, YearMonth.of(2026, 10), TODAY);

        assertThat(comparison.isPartial()).isTrue();
        assertThat(comparison.throughDay()).isEqualTo(8);
        assertThat(comparison.total().percentText()).isEqualTo("-20.0%");
        assertThat(comparison.total().isDown()).isTrue();
        assertThat(comparison.biggestIncrease()).isEmpty();
    }

    @Test
    void sameDaysAreCappedAtTheEndOfAShorterPreviousMonth() {
        givenTotals("2026-03-01", "2026-03-31", total(Category.RENT, "4500"));
        givenTotals("2026-02-01", "2026-02-28", total(Category.RENT, "4500"));

        MonthComparison comparison = insightsService.compareWithPreviousMonth(
                USER_ID, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 31));

        assertThat(comparison.categories()).singleElement()
                .satisfies(row -> assertThat(row.change().difference()).isEqualByComparingTo("0"));
    }

    @Test
    void noSpendingInEitherMonthGivesAnEmptyComparison() {
        MonthComparison comparison = insightsService.compareWithPreviousMonth(USER_ID, YearMonth.of(2026, 9), TODAY);

        assertThat(comparison.categories()).isEmpty();
        assertThat(comparison.total().percent()).isNull();
        assertThat(comparison.total().isUp()).isFalse();
        assertThat(comparison.total().isDown()).isFalse();
    }

    @Test
    void changePercentIsRoundedToOneDecimal() {
        Change change = new Change(new BigDecimal("400"), new BigDecimal("300"));

        assertThat(change.percent()).isEqualByComparingTo("33.3");
        assertThat(change.isUp()).isTrue();
        assertThat(new Change(new BigDecimal("200"), new BigDecimal("300")).percentText()).isEqualTo("-33.3%");
        assertThat(new Change(new BigDecimal("50"), BigDecimal.ZERO).isNew()).isTrue();
    }

    // --- Daily average and end-of-month projection ---

    @Test
    void projectionContinuesTheDailyPaceToTheEndOfTheMonth() {
        SpendingPace pace = SpendingPace.calculate(OCTOBER, LocalDate.of(2026, 10, 10),
                money("1000"), money("0"), money("0"));

        assertThat(pace.daysCounted()).isEqualTo(10);
        assertThat(pace.daysLeft()).isEqualTo(21);
        assertThat(pace.dailyAverage()).isEqualByComparingTo("100.00");
        assertThat(pace.projection()).isEqualByComparingTo("3100.00"); // 1000 + 100 x 21
    }

    @Test
    void recurringExpensesAreNotRepeatedEveryDay() {
        // 4,500 rent (added by a recurring expense) + 1,000 day-to-day spending over 10 days
        SpendingPace pace = SpendingPace.calculate(OCTOBER, LocalDate.of(2026, 10, 10),
                money("5500"), money("4500"), money("0"));

        assertThat(pace.dailyAverage()).isEqualByComparingTo("550.00");
        assertThat(pace.dayToDayAverage()).isEqualByComparingTo("100.00");
        assertThat(pace.projection()).isEqualByComparingTo("7600.00"); // not 550 x 31 = 17,050
    }

    @Test
    void recurringExpensesStillDueAreAdded() {
        SpendingPace pace = SpendingPace.calculate(OCTOBER, LocalDate.of(2026, 10, 10),
                money("5500"), money("4500"), money("419"));

        assertThat(pace.projection()).isEqualByComparingTo("8019.00");
    }

    @Test
    void onTheFirstDayOneDayCounts() {
        SpendingPace pace = SpendingPace.calculate(OCTOBER, LocalDate.of(2026, 10, 1), money("90"), money("0"), money("0"));

        assertThat(pace.dailyAverage()).isEqualByComparingTo("90.00");
        assertThat(pace.projection()).isEqualByComparingTo("2790.00");
    }

    @Test
    void onTheLastDayTheProjectionIsWhatWasSpent() {
        SpendingPace pace = SpendingPace.calculate(OCTOBER, LocalDate.of(2026, 10, 31), money("3100"), money("0"), money("0"));

        assertThat(pace.daysLeft()).isZero();
        assertThat(pace.projection()).isEqualByComparingTo("3100.00");
    }

    @Test
    void projectionIsRoundedToTheCent() {
        SpendingPace pace = SpendingPace.calculate(OCTOBER, LocalDate.of(2026, 10, 3), money("100"), money("0"), money("0"));

        assertThat(pace.dailyAverage()).isEqualByComparingTo("33.33");
        assertThat(pace.projection()).isEqualByComparingTo("1033.33"); // 100 + 100 x 28 / 3
    }

    @Test
    void aFinishedMonthAveragesOverAllItsDaysAndTheTotalIsFinal() {
        SpendingPace pace = SpendingPace.calculate(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 8),
                money("3000"), money("0"), money("999"));

        assertThat(pace.finished()).isTrue();
        assertThat(pace.daysCounted()).isEqualTo(30);
        assertThat(pace.dailyAverage()).isEqualByComparingTo("100.00");
        assertThat(pace.projection()).isEqualByComparingTo("3000.00");
        assertThat(pace.upcomingRecurring()).isEqualByComparingTo("0");
    }

    @Test
    void paceUsesSpendingUpToTodayAndRecurringStillDueThisMonth() {
        when(expenseRepository.totalBetween(USER_ID, LocalDate.of(2026, 10, 1), TODAY)).thenReturn(new BigDecimal("800"));
        when(recurringRepository.upcomingTotal(USER_ID, TODAY, LocalDate.of(2026, 10, 31))).thenReturn(new BigDecimal("419"));

        SpendingPace pace = insightsService.pace(USER_ID, OCTOBER, TODAY).orElseThrow();

        assertThat(pace.dailyAverage()).isEqualByComparingTo("100.00");          // 800 over 8 days
        assertThat(pace.projection()).isEqualByComparingTo("3519.00");           // 800 + 100 x 23 + 419
        assertThat(insightsService.pace(USER_ID, YearMonth.of(2026, 11), TODAY)).isEmpty();
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private void givenTotals(String from, String to, CategoryTotal... totals) {
        when(expenseRepository.totalsByCategory(USER_ID, LocalDate.parse(from), LocalDate.parse(to)))
                .thenReturn(List.of(totals));
    }

    private static CategoryTotal total(Category category, String amount) {
        return new CategoryTotal(category, new BigDecimal(amount));
    }
}
