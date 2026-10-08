package com.example.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class GoalForm {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotNull(message = "Target is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Target must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Target can have at most 2 decimal places")
    private BigDecimal targetAmount;

    // Optional
    @FutureOrPresent(message = "Target date can't be in the past")
    private LocalDate targetDate;

    // Optional: money already put aside
    @DecimalMin(value = "0.00", message = "Already saved can't be negative")
    @Digits(integer = 10, fraction = 2, message = "Already saved can have at most 2 decimal places")
    private BigDecimal alreadySaved;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public BigDecimal getAlreadySaved() {
        return alreadySaved;
    }

    public void setAlreadySaved(BigDecimal alreadySaved) {
        this.alreadySaved = alreadySaved;
    }
}
