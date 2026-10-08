package com.example.expenses.service;

import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.Category;
import com.example.expenses.model.PaymentMethod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads expenses from a CSV file: the app's own export format, or a bank export with at least
 * title/description, amount and date columns. Every row is checked with the same rules as the
 * add-expense form, and nothing is imported unless every row is valid.
 */
@Service
public class CsvImportService {

    public static final int MAX_ROWS = 2000;
    static final int MAX_ERRORS_SHOWN = 20;

    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d/M/uuuu");

    private final Validator validator;
    private final CategorySuggester categorySuggester;

    public CsvImportService(Validator validator, CategorySuggester categorySuggester) {
        this.validator = validator;
        this.categorySuggester = categorySuggester;
    }

    /** Either the expenses to import or the reasons the file can't be imported, never both. */
    public record Result(List<ExpenseForm> expenses, List<String> errors) {

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * One data row: the expense it describes and what's wrong with it, if anything.
     *
     * @param rowNumber the row as a spreadsheet shows it (the header is row 1)
     * @param deletedAt set when the row came from the trash (backup files have a deleted_at column)
     */
    public record Row(int rowNumber, ExpenseForm expense, Instant deletedAt, List<String> errors)
            implements Serializable {

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /** A problem with the whole file (empty, missing columns, too big), or one Row per data row. */
    public record Rows(String fileError, List<Row> rows) {
    }

    /** Reads every row and checks it with the add-expense rules, without deciding what to do with bad rows. */
    public Rows readRows(String csv) {
        List<List<String>> rows = Csv.parse(csv);
        if (rows.isEmpty()) {
            return new Rows("The file is empty.", List.of());
        }
        Map<String, Integer> columns = columnIndexes(rows.get(0));
        List<String> missing = new ArrayList<>();
        for (String required : List.of("title", "amount", "date")) {
            if (!columns.containsKey(required)) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            return new Rows("The first row must name the columns. Missing: " + String.join(", ", missing)
                    + ". Expected title, amount, date, and optionally category and note.", List.of());
        }
        if (rows.size() - 1 > MAX_ROWS) {
            return new Rows("The file has " + (rows.size() - 1) + " rows. The limit is " + MAX_ROWS + " per import.",
                    List.of());
        }

        List<Row> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> rowErrors = new ArrayList<>();
            ExpenseForm form = toForm(rows.get(i), columns, rowErrors);
            Instant deletedAt = null;
            String deleted = cell(rows.get(i), columns, "deleted_at");
            if (StringUtils.hasText(deleted)) {
                try {
                    deletedAt = Instant.parse(deleted);
                } catch (DateTimeParseException e) {
                    rowErrors.add("\"" + deleted + "\" is not a valid deleted_at time");
                }
            }
            if (rowErrors.isEmpty()) {
                validator.validate(form).stream()
                        .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                        .map(ConstraintViolation::getMessage)
                        .forEach(rowErrors::add);
            }
            result.add(new Row(i + 1, form, deletedAt, List.copyOf(rowErrors)));
        }
        return new Rows(null, result);
    }

    /** For the Import page: every row must be valid, or nothing is imported. */
    public Result read(String csv) {
        Rows rows = readRows(csv);
        if (rows.fileError() != null) {
            return failure(rows.fileError());
        }
        List<ExpenseForm> expenses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Row row : rows.rows()) {
            row.errors().forEach(error -> errors.add("Row " + row.rowNumber() + ": " + error));
            expenses.add(row.expense());
        }
        if (errors.isEmpty()) {
            return new Result(expenses, List.of());
        }
        if (errors.size() > MAX_ERRORS_SHOWN) {
            int more = errors.size() - MAX_ERRORS_SHOWN;
            List<String> shown = new ArrayList<>(errors.subList(0, MAX_ERRORS_SHOWN));
            shown.add("...and " + more + " more.");
            return new Result(List.of(), shown);
        }
        return new Result(List.of(), errors);
    }

    private ExpenseForm toForm(List<String> row, Map<String, Integer> columns, List<String> errors) {
        ExpenseForm form = new ExpenseForm();
        form.setTitle(cell(row, columns, "title"));

        String amount = cell(row, columns, "amount");
        if (StringUtils.hasText(amount)) {
            parseAmount(amount).ifPresentOrElse(form::setAmount,
                    () -> errors.add("\"" + amount + "\" is not a valid amount"));
        }

        String date = cell(row, columns, "date");
        if (StringUtils.hasText(date)) {
            parseDate(date).ifPresentOrElse(form::setDate,
                    () -> errors.add("\"" + date + "\" is not a valid date (use 2026-10-31 or 31/10/2026)"));
        }

        String category = cell(row, columns, "category");
        if (StringUtils.hasText(category)) {
            parseCategory(category).ifPresentOrElse(form::setCategory,
                    () -> errors.add("\"" + category + "\" is not a category"));
        } else {
            form.setCategory(categorySuggester.suggest(form.getTitle()).orElse(Category.OTHER));
        }

        String paymentMethod = cell(row, columns, "payment_method");
        if (StringUtils.hasText(paymentMethod)) {
            PaymentMethod.parse(paymentMethod).ifPresentOrElse(form::setPaymentMethod,
                    () -> errors.add("\"" + paymentMethod + "\" is not a payment method"));
        }

        String note = cell(row, columns, "note");
        form.setNote(StringUtils.hasText(note) ? note : null);
        return form;
    }

    private static Map<String, Integer> columnIndexes(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).trim().toLowerCase(Locale.ROOT);
            if (name.equals("description")) {
                name = "title"; // common in bank exports
            }
            columns.putIfAbsent(name, i);
        }
        return columns;
    }

    private static String cell(List<String> row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= row.size()) {
            return null;
        }
        return Csv.unescapeFormula(row.get(index).trim());
    }

    /** Accepts "1,234.50", "฿85", "85 THB". Negative or zero amounts are caught by validation. */
    static Optional<BigDecimal> parseAmount(String text) {
        String cleaned = text.replace(",", "")
                .replace("฿", "")
                .replaceAll("(?i)thb|baht", "")
                .trim();
        try {
            return Optional.of(new BigDecimal(cleaned));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Optional<LocalDate> parseDate(String text) {
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeParseException e) {
            // fall through to day/month/year
        }
        try {
            return Optional.of(LocalDate.parse(text, DAY_MONTH_YEAR));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<Category> parseCategory(String text) {
        for (Category category : Category.values()) {
            if (category.name().equalsIgnoreCase(text) || category.getLabel().equalsIgnoreCase(text)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    private static Result failure(String error) {
        return new Result(List.of(), List.of(error));
    }
}
