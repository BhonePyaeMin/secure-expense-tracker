package com.example.expenses;

import com.example.expenses.model.SavingsGoal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavingsGoalTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Test
    void progressRemainingAndPercent() {
        SavingsGoal goal = goal("35000", "12000", null);

        assertThat(goal.getPercent()).isEqualByComparingTo("34.3");
        assertThat(goal.getRemaining()).isEqualByComparingTo("23000");
        assertThat(goal.isReached()).isFalse();
    }

    @Test
    void monthlyAmountCountsThisMonthAndTheTargetMonthAndRoundsUp() {
        // October to March is 6 months; 23,000 / 6 = 3,833.333... -> 3,833.34 so it's never short
        SavingsGoal goal = goal("35000", "12000", LocalDate.of(2027, 3, 31));

        assertThat(goal.monthlyNeeded(TODAY)).isEqualByComparingTo("3833.34");
    }

    @Test
    void targetThisMonthNeedsEverythingThisMonth() {
        assertThat(goal("1000", "400", LocalDate.of(2026, 10, 31)).monthlyNeeded(TODAY)).isEqualByComparingTo("600.00");
    }

    @Test
    void noMonthlyAmountWithoutADateOnceReachedOrWhenOverdue() {
        assertThat(goal("1000", "0", null).monthlyNeeded(TODAY)).isNull();
        assertThat(goal("1000", "1000", LocalDate.of(2027, 1, 1)).monthlyNeeded(TODAY)).isNull();

        SavingsGoal late = goal("1000", "100", LocalDate.of(2026, 9, 30));
        assertThat(late.monthlyNeeded(TODAY)).isNull();
        assertThat(late.isOverdue(TODAY)).isTrue();
    }

    @Test
    void savingPastTheTargetCountsAsReachedAndTheBarStopsAtFullWidth() {
        SavingsGoal goal = goal("1000", "900", null);
        goal.deposit(new BigDecimal("300"));

        assertThat(goal.isReached()).isTrue();
        assertThat(goal.getPercent()).isEqualByComparingTo("120.0");
        assertThat(goal.getBarPercent()).isEqualByComparingTo("100");
        assertThat(goal.getRemaining()).isEqualByComparingTo("0");
    }

    @Test
    void cannotTakeOutMoreThanIsSaved() {
        SavingsGoal goal = goal("1000", "200", null);

        assertThatThrownBy(() -> goal.withdraw(new BigDecimal("200.01"))).isInstanceOf(IllegalArgumentException.class);
        goal.withdraw(new BigDecimal("200"));
        assertThat(goal.getSavedAmount()).isEqualByComparingTo("0");
    }

    private static SavingsGoal goal(String target, String saved, LocalDate by) {
        return new SavingsGoal(null, "Laptop", new BigDecimal(target), new BigDecimal(saved), by);
    }
}
