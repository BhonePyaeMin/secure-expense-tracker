package com.example.expenses;

import com.example.expenses.web.MoneyFormatter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyFormatterTest {

    private final MoneyFormatter baht = new MoneyFormatter("฿", "THB");

    @Test
    void formatsWithSymbolThousandsSeparatorAndTwoDecimals() {
        assertThat(baht.format(new BigDecimal("1234.5"))).isEqualTo("฿1,234.50");
        assertThat(baht.format(new BigDecimal("60"))).isEqualTo("฿60.00");
    }

    @Test
    void negativeAmountsPutTheSignBeforeTheSymbol() {
        assertThat(baht.format(new BigDecimal("-20.00"))).isEqualTo("-฿20.00");
    }

    @Test
    void nullIsBlank() {
        assertThat(baht.format(null)).isEmpty();
    }

    @Test
    void currencyIsConfigurable() {
        assertThat(new MoneyFormatter("$", "USD").format(new BigDecimal("9.99"))).isEqualTo("$9.99");
    }
}
