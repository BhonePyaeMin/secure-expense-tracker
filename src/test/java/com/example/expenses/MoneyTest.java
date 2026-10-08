package com.example.expenses;

import com.example.expenses.model.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The app's money rules: 2 decimal places, HALF_UP. */
class MoneyTest {

    @ParameterizedTest(name = "{0} / {1} = {2}")
    @CsvSource({
            "10.00, 3, 3.33",     // 3.333... rounds down
            "20.00, 3, 6.67",     // 6.666... rounds up
            "0.05, 2, 0.03",      // exactly half a cent (0.025) rounds up
            "0.01, 2, 0.01",      // 0.005 rounds up
            "0.01, 3, 0.00",      // 0.0033 rounds down
            "-10.00, 3, -3.33",
            "-0.05, 2, -0.03",    // HALF_UP rounds halves away from zero
            "100.00, 7, 14.29",
            "3100.00, 31, 100.00",
    })
    void divisionRoundsToTheCentHalfUp(String amount, long divisor, String expected) {
        BigDecimal result = Money.divide(new BigDecimal(amount), divisor);

        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"1.005, 1.01", "1.004, 1.00", "2.5, 2.50", "0, 0.00", "-1.005, -1.01"})
    void amountsAreKeptToTwoDecimals(String amount, String expected) {
        assertThat(Money.of(new BigDecimal(amount))).isEqualByComparingTo(expected).hasScaleOf(2);
    }

    @Test
    void totalsAreExactAndInCents() {
        assertThat(Money.total(List.of(new BigDecimal("0.10"), new BigDecimal("0.20")))).isEqualTo(new BigDecimal("0.30"));
        assertThat(Money.total(List.of())).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void percentagesHaveOneDecimal() {
        assertThat(Money.percent(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("33.3");
        assertThat(Money.percent(new BigDecimal("2"), new BigDecimal("3"))).isEqualByComparingTo("66.7");
        assertThat(Money.percent(new BigDecimal("5"), BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void dividingByZeroIsRefused() {
        assertThatThrownBy(() -> Money.divide(new BigDecimal("10.00"), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
