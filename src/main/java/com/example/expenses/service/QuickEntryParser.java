package com.example.expenses.service;

import com.example.expenses.dto.ExpenseForm;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a line like "lunch 85 baht yesterday" into a filled-in expense form, with plain rules
 * (no AI). The user always reviews the form before saving.
 * <p>
 * Order matters: dates are taken out first so "3 days ago" or "1/10" aren't read as amounts,
 * then the amount, and whatever is left becomes the title.
 */
@Service
public class QuickEntryParser {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    // A number must stand alone: "7-11" or "2nd" never count as amounts
    private static final String NOT_AFTER = "(?<![\\p{L}\\p{N}\\-/.,:])";
    private static final String NOT_BEFORE = "(?![\\p{L}\\p{N}\\-/:]|[.,]\\d)";
    // Real month names only, so "5 marathon" is not March 5
    private static final String MONTHS = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?"
            + "|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)";

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern SLASH_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}|\\d{4}))?\\b");
    private static final Pattern DAY_MONTH = Pattern.compile("\\b(\\d{1,2})\\s+" + MONTHS + "\\b", FLAGS);
    private static final Pattern MONTH_DAY = Pattern.compile("\\b" + MONTHS + "\\s+(\\d{1,2})\\b", FLAGS);
    private static final Pattern DAYS_AGO = Pattern.compile("\\b(\\d{1,3})\\s+days?\\s+ago\\b", FLAGS);
    private static final Pattern DAY_BEFORE_YESTERDAY = Pattern.compile("\\bday before yesterday\\b", FLAGS);
    private static final Pattern YESTERDAY = Pattern.compile("\\byesterday\\b|เมื่อวาน", FLAGS);
    private static final Pattern TODAY = Pattern.compile("\\btoday\\b|วันนี้", FLAGS);
    private static final Pattern WEEKDAY = Pattern.compile(
            "\\b(last\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", FLAGS);

    private static final Pattern AMOUNT = Pattern.compile(NOT_AFTER
            + "(?:฿\\s*)?(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d{1,2}))?"
            + "(?:\\s*(?:฿|baht|thb|บาท|b\\b))?" + NOT_BEFORE, FLAGS);
    private static final Pattern CURRENCY_WORD = Pattern.compile("\\b(baht|thb)\\b|บาท|฿", FLAGS);
    private static final Pattern EDGE_FILLER = Pattern.compile(
            "^(?:(?:for|on|at|in)\\b|[-,.:;])\\s*|\\s*(?:\\b(?:for|on|at|in)|[-,.:;])$", FLAGS);

    private final CategorySuggester categorySuggester;

    public QuickEntryParser(CategorySuggester categorySuggester) {
        this.categorySuggester = categorySuggester;
    }

    public ExpenseForm parse(String text, LocalDate today) {
        ExpenseForm form = new ExpenseForm();
        String rest = text == null ? "" : text.strip();

        Extracted<LocalDate> date = extractDate(rest, today);
        form.setDate(date.value() != null ? date.value() : today);
        rest = date.rest();

        Matcher amount = AMOUNT.matcher(rest);
        if (amount.find()) {
            String digits = amount.group(1).replace(",", "");
            String decimals = amount.group(2);
            form.setAmount(new BigDecimal(decimals == null ? digits : digits + "." + decimals));
            rest = cut(rest, amount);
        }
        rest = CURRENCY_WORD.matcher(rest).replaceAll(" ");

        String title = tidy(rest);
        form.setTitle(title);
        categorySuggester.suggest(title).ifPresent(form::setCategory);
        return form;
    }

    private record Extracted<T>(T value, String rest) {
    }

    private static Extracted<LocalDate> extractDate(String text, LocalDate today) {
        Extracted<LocalDate> found;
        if ((found = tryPattern(text, ISO_DATE, m -> LocalDate.of(num(m, 1), num(m, 2), num(m, 3)))) != null) {
            return found;
        }
        if ((found = tryPattern(text, SLASH_DATE, m -> {
            int year = m.group(3) == null ? -1 : num(m, 3) < 100 ? 2000 + num(m, 3) : num(m, 3);
            return withYear(today, year, num(m, 2), num(m, 1));
        })) != null) {
            return found;
        }
        if ((found = tryPattern(text, DAY_MONTH, m -> withYear(today, -1, monthOf(m.group(2)), num(m, 1)))) != null) {
            return found;
        }
        if ((found = tryPattern(text, MONTH_DAY, m -> withYear(today, -1, monthOf(m.group(1)), num(m, 2)))) != null) {
            return found;
        }
        if ((found = tryPattern(text, DAYS_AGO, m -> today.minusDays(num(m, 1)))) != null) {
            return found;
        }
        if ((found = tryPattern(text, DAY_BEFORE_YESTERDAY, m -> today.minusDays(2))) != null) {
            return found;
        }
        if ((found = tryPattern(text, YESTERDAY, m -> today.minusDays(1))) != null) {
            return found;
        }
        if ((found = tryPattern(text, TODAY, m -> today)) != null) {
            return found;
        }
        if ((found = tryPattern(text, WEEKDAY, m -> {
            DayOfWeek day = DayOfWeek.valueOf(m.group(2).toUpperCase(Locale.ROOT));
            return m.group(1) != null
                    ? today.with(TemporalAdjusters.previous(day))
                    : today.with(TemporalAdjusters.previousOrSame(day));
        })) != null) {
            return found;
        }
        return new Extracted<>(null, text);
    }

    private static Extracted<LocalDate> tryPattern(String text, Pattern pattern, Function<Matcher, LocalDate> toDate) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new Extracted<>(toDate.apply(matcher), cut(text, matcher));
        } catch (DateTimeException e) {
            return null; // e.g. 31/02: not a date, leave the text alone
        }
    }

    /** A date without a year means the most recent one: "25/12" in October is last Christmas. */
    private static LocalDate withYear(LocalDate today, int year, int month, int day) {
        if (year > 0) {
            return LocalDate.of(year, month, day);
        }
        LocalDate thisYear = LocalDate.of(today.getYear(), month, day);
        return thisYear.isAfter(today) ? thisYear.minusYears(1) : thisYear;
    }

    private static int monthOf(String name) {
        String prefix = name.substring(0, 3).toUpperCase(Locale.ROOT);
        for (Month month : Month.values()) {
            if (month.name().startsWith(prefix)) {
                return month.getValue();
            }
        }
        throw new DateTimeException("Unknown month " + name);
    }

    private static int num(Matcher matcher, int group) {
        return Integer.parseInt(matcher.group(group));
    }

    private static String cut(String text, Matcher matcher) {
        return text.substring(0, matcher.start()) + " " + text.substring(matcher.end());
    }

    private static String tidy(String text) {
        String title = text.replaceAll("\\s+", " ").strip();
        String previous;
        do {
            previous = title;
            title = EDGE_FILLER.matcher(title).replaceAll("").strip();
        } while (!title.equals(previous));
        if (!StringUtils.hasText(title)) {
            return "";
        }
        return Character.toUpperCase(title.charAt(0)) + title.substring(1);
    }
}
