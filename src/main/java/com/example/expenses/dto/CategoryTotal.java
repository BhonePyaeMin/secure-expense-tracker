package com.example.expenses.dto;

import com.example.expenses.model.Category;

import java.math.BigDecimal;

/** Total spent in one category, produced directly by a grouped JPQL query. */
public record CategoryTotal(Category category, BigDecimal total) {
}
