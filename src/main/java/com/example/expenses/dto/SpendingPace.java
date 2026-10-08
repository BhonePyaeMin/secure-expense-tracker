package com.example.expenses.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Daily average and end-of-month projection for one month.
 *
 * @param daysCounted     days of the month so far, today included (all days for a finished month)
 * @param dayToDayAverage the daily average without recurring expenses (rent, subscriptions), which
 *                        happen once a month rather than every day
 * @param fixedSpent      recurring expenses already added this month
 * @param upcomingRecurring recurring expenses still due later this month
 * @param projection      expected total by the end of the month; the actual total once it's over
 */
public record SpendingPace(BigDecimal spent,
                           int daysCounted,
                           int daysInMonth,
                           BigDecimal dailyAverage,
                           BigDecimal dayToDayAverage,
                           BigDecimal fixedSpent,
                           BigDecimal upcomingRecurring,
                           BigDecimal projection,
                           boolean finished) {

    public int daysLeft() {
        return daysInMonth - daysCounted;
    }

    /**
     * projection = spent so far + day-to-day average × days left + recurring expenses still due.
     * A plain "average × days in month" would treat rent paid on the 1st as if it were spent every day.
     */
    public static SpendingPace calculate(YearMonth month, LocalDate today, BigDecimal spent,
                                         BigDecimal fixedSpent, BigDecimal upcomingRecurring) {
        if (month.isAfter(YearMonth.from(today))) {
            throw new IllegalArgumentException("No pace for a month that hasn't started: " + month);
        }
        boolean finished = month.isBefore(YearMonth.from(today));
        int daysInMonth = month.lengthOfMonth();
        int daysCounted = finished ? daysInMonth : today.getDayOfMonth();
        int daysLeft = daysInMonth - daysCounted;
        BigDecimal days = BigDecimal.valueOf(daysCounted);

        BigDecimal dayToDay = spent.subtract(fixedSpent).max(BigDecimal.ZERO);
        BigDecimal dailyAverage = spent.divide(days, 2, RoundingMode.HALF_UP);
        BigDecimal dayToDayAverage = dayToDay.divide(days, 2, RoundingMode.HALF_UP);
        BigDecimal projection = finished
                ? spent
                : spent.add(dayToDay.multiply(BigDecimal.valueOf(daysLeft)).divide(days, 2, RoundingMode.HALF_UP))
                        .add(upcomingRecurring);

        return new SpendingPace(money(spent), daysCounted, daysInMonth, dailyAverage, dayToDayAverage,
                money(fixedSpent), money(finished ? BigDecimal.ZERO : upcomingRecurring), money(projection), finished);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
