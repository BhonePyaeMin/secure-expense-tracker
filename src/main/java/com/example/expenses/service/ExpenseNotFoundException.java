package com.example.expenses.service;

public class ExpenseNotFoundException extends RuntimeException {

    public ExpenseNotFoundException(Long id) {
        super("Expense " + id + " not found");
    }
}
