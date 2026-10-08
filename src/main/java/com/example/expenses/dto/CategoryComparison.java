package com.example.expenses.dto;

import com.example.expenses.model.Category;

/** One category's spending this month next to last month. */
public record CategoryComparison(Category category, Change change) {
}
