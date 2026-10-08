package com.example.expenses.config;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

/**
 * Replaces the long stack trace you get when the app can't open its database with a short
 * explanation. By far the most common cause is starting the app while another copy is still
 * running: an H2 file database can only be opened by one program at a time.
 * <p>
 * Hibernate catches H2's "Database may be already in use" error, logs it, and then fails with
 * "Unable to determine Dialect without JDBC metadata" (without the H2 error as its cause),
 * so both messages are recognised. Registered in META-INF/spring.factories.
 */
public class DatabaseInUseFailureAnalyzer implements FailureAnalyzer {

    private static final String H2_IN_USE = "Database may be already in use";
    private static final String NO_CONNECTION = "Unable to determine Dialect without JDBC metadata";

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && (message.contains(H2_IN_USE) || message.contains(NO_CONNECTION))) {
                return new FailureAnalysis(
                        "The app could not open its database. Most likely another copy of this app is still "
                                + "running and has the database file open (H2 lets only one program use it at a time). "
                                + "Look for \"Database may be already in use\" in the log above to confirm.",
                        "Stop the other copy (press Ctrl+C in the terminal where it runs, or close that terminal), "
                                + "then start the app again.",
                        failure);
            }
        }
        return null;
    }
}
