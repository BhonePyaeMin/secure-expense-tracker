package com.example.expenses.model;

import jakarta.persistence.AttributeConverter;

/**
 * Stores an enum as its name in a plain varchar column.
 * <p>
 * With {@code @Enumerated} Hibernate 6 adds a CHECK constraint listing the allowed values, and
 * {@code ddl-auto=update} never changes that constraint. Adding a new enum value would then break
 * inserts on an existing database. A converter column has no such constraint.
 */
public abstract class EnumNameConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected EnumNameConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.name();
    }

    @Override
    public E convertToEntityAttribute(String name) {
        return name == null ? null : Enum.valueOf(type, name);
    }
}
