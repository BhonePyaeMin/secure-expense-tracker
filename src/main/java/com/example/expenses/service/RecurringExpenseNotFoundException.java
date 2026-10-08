package com.example.expenses.service;

public class RecurringExpenseNotFoundException extends NotFoundException {

    public RecurringExpenseNotFoundException(Long id) {
        super("Recurring expense", id);
    }
}
