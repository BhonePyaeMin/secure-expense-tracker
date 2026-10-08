package com.example.expenses.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Formats amounts with the currency set in application.properties (app.currency.*).
 * Templates call it as {@code ${@money.format(amount)}}.
 */
@Component("money")
public class MoneyFormatter {

    private final String symbol;
    private final String code;

    public MoneyFormatter(@Value("${app.currency.symbol:฿}") String symbol,
                          @Value("${app.currency.code:THB}") String code) {
        this.symbol = symbol;
        this.code = code;
    }

    /** 1234.5 becomes "฿1,234.50"; -20 becomes "-฿20.00". */
    public String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        String digits = String.format(Locale.US, "%,.2f", amount.abs());
        return (amount.signum() < 0 ? "-" : "") + symbol + digits;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCode() {
        return code;
    }
}
