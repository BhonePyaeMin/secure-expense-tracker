package com.example.expenses.dto;

import com.example.expenses.model.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class BudgetForm {

    @NotNull(message = "Choose a category")
    private Category category;

    @NotNull(message = "Limit is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Limit must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Limit can have at most 2 decimal places")
    private BigDecimal monthlyLimit;

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }
}
