package com.example.expenses.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CategoryConverter extends EnumNameConverter<Category> {

    public CategoryConverter() {
        super(Category.class);
    }
}
