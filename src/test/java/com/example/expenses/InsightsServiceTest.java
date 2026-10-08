package com.example.expenses;

import com.example.expenses.dto.CategoryComparison;
import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.Change;
import com.example.expenses.dto.MonthComparison;
import com.example.expenses.model.Category;
import com.example.expenses.repository.ExpenseRepository;
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
    private final InsightsService insightsService = new InsightsService(expenseRepository);

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

    private void givenTotals(String from, String to, CategoryTotal... totals) {
        when(expenseRepository.totalsByCategory(USER_ID, LocalDate.parse(from), LocalDate.parse(to)))
                .thenReturn(List.of(totals));
    }

    private static CategoryTotal total(Category category, String amount) {
        return new CategoryTotal(category, new BigDecimal(amount));
    }
}
