package com.example.expenses.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Total spent on one day, produced directly by a grouped JPQL query. */
public record DailyTotal(LocalDate date, BigDecimal total) {
}
