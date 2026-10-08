package com.example.expenses.dto;

import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the add/edit form binds to. Keeping it separate from the entity means
 * a request can never set fields like the id.
 */
public class ExpenseForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must be at most 100 characters")
    private String title;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Choose a category")
    private Category category;

    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Date cannot be in the future")
    private LocalDate date;

    @Size(max = 255, message = "Note must be at most 255 characters")
    private String note;

    // Optional: older expenses and some imports don't have one
    private PaymentMethod paymentMethod;

    public static ExpenseForm from(Expense expense) {
        ExpenseForm form = new ExpenseForm();
        form.setTitle(expense.getTitle());
        form.setAmount(expense.getAmount());
        form.setCategory(expense.getCategory());
        form.setDate(expense.getDate());
        form.setNote(expense.getNote());
        form.setPaymentMethod(expense.getPaymentMethod());
        return form;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
