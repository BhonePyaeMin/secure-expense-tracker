package com.example.expenses.model;

import java.util.Optional;

public enum PaymentMethod {
    CASH("Cash"),
    BANK("Bank transfer"),
    PROMPTPAY("PromptPay"),
    EWALLET("E-wallet"),
    CARD("Card");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Matches "PROMPTPAY", "promptpay" or "PromptPay" (used by CSV import). */
    public static Optional<PaymentMethod> parse(String text) {
        for (PaymentMethod method : values()) {
            if (method.name().equalsIgnoreCase(text.trim()) || method.label.equalsIgnoreCase(text.trim())) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}
