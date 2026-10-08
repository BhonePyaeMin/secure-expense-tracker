package com.example.expenses.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentMethodConverter extends EnumNameConverter<PaymentMethod> {

    public PaymentMethodConverter() {
        super(PaymentMethod.class);
    }
}
