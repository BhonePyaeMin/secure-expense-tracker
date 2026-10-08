package com.example.expenses.model;

public enum AuditAction {
    REGISTERED("Account created"),
    LOGIN_SUCCEEDED("Signed in"),
    LOGIN_FAILED("Failed sign-in"),
    ACCOUNT_LOCKED("Account locked"),
    EXPENSE_CREATED("Expense added"),
    EXPENSE_UPDATED("Expense edited"),
    EXPENSE_DELETED("Expense deleted"),
    BUDGET_SET("Budget set"),
    BUDGET_REMOVED("Budget removed");

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
