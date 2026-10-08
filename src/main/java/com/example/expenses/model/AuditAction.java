package com.example.expenses.model;

public enum AuditAction {
    REGISTERED("Account created"),
    LOGIN_SUCCEEDED("Signed in"),
    LOGIN_FAILED("Failed sign-in"),
    ACCOUNT_LOCKED("Account locked"),
    EXPENSE_CREATED("Expense added"),
    EXPENSE_UPDATED("Expense edited"),
    EXPENSE_DELETED("Moved to trash"),
    EXPENSE_RESTORED("Restored from trash"),
    EXPENSE_PURGED("Deleted forever"),
    EXPENSES_IMPORTED("Expenses imported"),
    BUDGET_SET("Budget set"),
    BUDGET_REMOVED("Budget removed"),
    INCOME_ADDED("Income added"),
    INCOME_DELETED("Income deleted"),
    RECURRING_CREATED("Recurring expense set up"),
    RECURRING_PAUSED("Recurring expense paused"),
    RECURRING_RESUMED("Recurring expense resumed"),
    RECURRING_DELETED("Recurring expense deleted"),
    RECURRING_ADDED("Recurring expense added");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSecurityEvent() {
        return this == LOGIN_FAILED || this == ACCOUNT_LOCKED;
    }
}
