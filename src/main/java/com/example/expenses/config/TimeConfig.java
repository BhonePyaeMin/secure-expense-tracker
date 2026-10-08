package com.example.expenses.config;

import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * One place for "now". Services and controllers get this Clock injected instead of calling
 * LocalDate.now(), so "today", month boundaries and "days left" follow Bangkok time even on a
 * server set to UTC, and tests can use a fixed clock.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }

    /** "Not in the future" checks (@PastOrPresent) use the same clock as the rest of the app. */
    @Bean
    ValidationConfigurationCustomizer validationClock(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
