package com.example.expenses.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Turns due recurring expenses into real expenses. Scheduling is switched on with
 * {@code @EnableScheduling} on the application class.
 */
@Component
@Lazy(false) // lazy initialization is on for the whole app, but @Scheduled only works on beans created at startup
public class RecurringExpenseScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseScheduler.class);

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseScheduler(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    // Every day at 00:05 by default
    @Scheduled(cron = "${app.recurring.cron:0 5 0 * * *}")
    public void addDueExpensesDaily() {
        run();
    }

    // The app may not have been running on the due date (it's a laptop app), so catch up at startup too
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        run();
    }

    // synchronized: the startup catch-up and the daily job never run at the same time in this app
    private synchronized void run() {
        try {
            int added = recurringExpenseService.addDueExpenses(LocalDate.now());
            if (added > 0) {
                log.info("Added {} recurring expense(s)", added);
            }
        } catch (DataIntegrityViolationException e) {
            // Another run already added these; this run was rolled back, so nothing is duplicated
            log.warn("Recurring expenses were already added by another run; skipped");
        }
    }
}
