package com.example.expenses.model;

public enum Category {
    FOOD("Food"),
    TRANSPORT("Transport"),
    RENT("Rent"),
    STUDY("Study"),
    HEALTH("Health"),
    FUN("Fun"),
    OTHER("Other");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
