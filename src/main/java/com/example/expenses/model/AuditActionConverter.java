package com.example.expenses.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AuditActionConverter extends EnumNameConverter<AuditAction> {

    public AuditActionConverter() {
        super(AuditAction.class);
    }
}
