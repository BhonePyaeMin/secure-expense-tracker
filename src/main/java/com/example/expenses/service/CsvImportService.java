package com.example.expenses.service;

import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.Category;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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

    public Result read(String csv) {
        List<List<String>> rows = Csv.parse(csv);
        if (rows.isEmpty()) {
            return failure("The file is empty.");
        }
        Map<String, Integer> columns = columnIndexes(rows.get(0));
        List<String> missing = new ArrayList<>();
        for (String required : List.of("title", "amount", "date")) {
            if (!columns.containsKey(required)) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            return failure("The first row must name the columns. Missing: " + String.join(", ", missing)
                    + ". Expected title, amount, date, and optionally category and note.");
        }
        if (rows.size() - 1 > MAX_ROWS) {
            return failure("The file has " + (rows.size() - 1) + " rows. The limit is " + MAX_ROWS + " per import.");
        }

        List<ExpenseForm> expenses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> rowErrors = new ArrayList<>();
            ExpenseForm form = toForm(rows.get(i), columns, rowErrors);
            if (rowErrors.isEmpty()) {
                validator.validate(form).stream()
                        .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                        .map(ConstraintViolation::getMessage)
                        .forEach(rowErrors::add);
            }
            int rowNumber = i + 1; // the header is row 1, like in a spreadsheet
            rowErrors.forEach(error -> errors.add("Row " + rowNumber + ": " + error));
            expenses.add(form);
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
