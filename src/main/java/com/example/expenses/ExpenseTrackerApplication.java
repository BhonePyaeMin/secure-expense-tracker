package com.example.expenses;

import com.example.expenses.config.TimeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling // runs RecurringExpenseScheduler
public class ExpenseTrackerApplication {

    public static void main(String[] args) {
        // Also set the JVM default, for anything outside our code that asks for the system time zone.
        // Our own code uses the injected Clock (TimeConfig) instead.
        TimeZone.setDefault(TimeZone.getTimeZone(TimeConfig.ZONE));
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }
}
