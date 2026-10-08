package com.example.expenses.dto;

import com.example.expenses.model.PaymentMethod;

import java.math.BigDecimal;

/**
 * Spending paid one way in a month.
 *
 * @param method  null for expenses recorded before payment methods existed ("Not set")
 * @param percent share of the month's spending, for the bar width
 */
public record PaymentMethodTotal(PaymentMethod method, BigDecimal total, BigDecimal percent) {

    /** Used by the grouped JPQL query, before the share is known. */
    public PaymentMethodTotal(PaymentMethod method, BigDecimal total) {
        this(method, total, null);
    }

    public String label() {
        return method == null ? "Not set" : method.getLabel();
    }
}
