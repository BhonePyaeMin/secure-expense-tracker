package com.example.expenses.service;

public class RecurringExpenseNotFoundException extends RuntimeException {

    public RecurringExpenseNotFoundException(Long id) {
        super("Recurring expense " + id + " not found");
    }
}
