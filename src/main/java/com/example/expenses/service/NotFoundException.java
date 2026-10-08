package com.example.expenses.service;

/** Something the user asked for doesn't exist or isn't theirs. Shown as a friendly 404 page. */
public class NotFoundException extends RuntimeException {

    private final String what;

    public NotFoundException(String what, Long id) {
        super(what + " " + id + " not found");
        this.what = what;
    }

    /** e.g. "Income" or "Recurring expense", for the page heading. */
    public String getWhat() {
        return what;
    }
}
