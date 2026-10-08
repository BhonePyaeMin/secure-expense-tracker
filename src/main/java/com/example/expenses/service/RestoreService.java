package com.example.expenses.service;

import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.AuditAction;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.User;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Restores expenses and budgets from a backup ZIP (from the Backup link) or an expenses CSV.
 * Two steps: {@link #preview} reads and checks every row without changing anything, then
 * {@link #apply} saves the valid rows. Bad rows are skipped and reported, never half-saved.
 */
@Service
public class RestoreService {

    // A 1 MB upload can't unzip into something huge (zip bomb)
    static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private final CsvImportService csvImportService;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RestoreService(CsvImportService csvImportService, ExpenseRepository expenseRepository,
                          BudgetRepository budgetRepository, UserRepository userRepository, AuditService auditService) {
        this.csvImportService = csvImportService;
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public record BudgetRow(int rowNumber, Category category, BigDecimal monthlyLimit, List<String> errors)
            implements Serializable {

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /** What a file contains, checked row by row. Kept in the session between preview and confirm. */
    public record Plan(String filename, List<CsvImportService.Row> expenses, List<BudgetRow> budgets,
                       List<String> fileErrors, List<String> ignoredFiles) implements Serializable {

        public long validExpenses() {
            return expenses.stream().filter(CsvImportService.Row::isValid).count();
        }

        public long invalidExpenses() {
            return expenses.size() - validExpenses();
        }

        public long validBudgets() {
            return budgets.stream().filter(BudgetRow::isValid).count();
        }

        public long invalidBudgets() {
            return budgets.size() - validBudgets();
        }

        public boolean hasAnythingToRestore() {
            return validExpenses() + validBudgets() > 0;
        }
    }

    public record Report(boolean replaced, long expensesDeleted, long budgetsDeleted, int expensesAdded,
                         int budgetsAdded, int budgetsKept, List<String> skipped) {
    }

    public record CurrentData(long expenses, long budgets) {
    }

    public CurrentData currentData(Long userId) {
        return new CurrentData(expenseRepository.countByOwnerId(userId), budgetRepository.countByOwnerId(userId));
    }

    /** Reads and checks the file. Changes nothing. */
    public Plan preview(String filename, byte[] content) throws IOException {
        Map<String, String> files = new HashMap<>();
        List<String> ignored = new ArrayList<>();
        boolean zip = content.length > 1 && content[0] == 'P' && content[1] == 'K';
        if (zip) {
            try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                    if (name.equals("expenses.csv") || name.equals("budgets.csv")) {
                        files.put(name, new String(readCapped(in), StandardCharsets.UTF_8));
                    } else if (!entry.isDirectory()) {
                        ignored.add(name);
                    }
                }
            }
        } else {
            files.put("expenses.csv", new String(content, StandardCharsets.UTF_8));
        }

        List<String> fileErrors = new ArrayList<>();
        if (files.isEmpty()) {
            fileErrors.add("The ZIP has no expenses.csv or budgets.csv. Use a file from the Backup link.");
        }
        List<CsvImportService.Row> expenses = List.of();
        if (files.containsKey("expenses.csv")) {
            CsvImportService.Rows rows = csvImportService.readRows(files.get("expenses.csv"));
            if (rows.fileError() != null) {
                fileErrors.add("expenses.csv: " + rows.fileError());
            } else {
                expenses = rows.rows();
            }
        }
        List<BudgetRow> budgets = files.containsKey("budgets.csv") ? readBudgets(files.get("budgets.csv"), fileErrors) : List.of();
        return new Plan(filename, expenses, budgets, fileErrors, ignored);
    }

    /**
     * Saves the valid rows for this user. In "add" mode existing data is kept, and a budget for a
     * category that already has one is left as it is. In "replace" mode the user's expenses
     * (including the trash) and budgets are deleted first. Other users' data is never touched.
     */
    @Transactional
    public Report apply(Long userId, Plan plan, boolean replace) {
        long expensesDeleted = 0;
        long budgetsDeleted = 0;
        if (replace) {
            expensesDeleted = expenseRepository.deleteByOwnerId(userId);
            budgetsDeleted = budgetRepository.deleteByOwnerId(userId);
        }
        User owner = userRepository.getReferenceById(userId);
        List<String> skipped = new ArrayList<>();

        int expensesAdded = 0;
        for (CsvImportService.Row row : plan.expenses()) {
            if (!row.isValid()) {
                skipped.add("expenses.csv row " + row.rowNumber() + ": " + String.join("; ", row.errors()));
                continue;
            }
            ExpenseForm form = row.expense();
            Expense expense = new Expense(owner, form.getTitle().trim(), form.getAmount(), form.getCategory(),
                    form.getDate(), StringUtils.hasText(form.getNote()) ? form.getNote().trim() : null);
            expense.setPaymentMethod(form.getPaymentMethod());
            if (row.deletedAt() != null) {
                expense.moveToTrash(row.deletedAt()); // it was in the trash when backed up
            }
            expenseRepository.save(expense);
            expensesAdded++;
        }

        int budgetsAdded = 0;
        int budgetsKept = 0;
        for (BudgetRow row : plan.budgets()) {
            if (!row.isValid()) {
                skipped.add("budgets.csv row " + row.rowNumber() + ": " + String.join("; ", row.errors()));
            } else if (budgetRepository.findByOwnerIdAndCategory(userId, row.category()).isPresent()) {
                budgetsKept++;
            } else {
                budgetRepository.save(new Budget(owner, row.category(), row.monthlyLimit()));
                budgetsAdded++;
            }
        }

        auditService.record(userId, AuditAction.DATA_RESTORED, (replace
                ? "Replaced " + expensesDeleted + " expenses and " + budgetsDeleted + " budgets; "
                : "") + "added " + expensesAdded + " expenses and " + budgetsAdded + " budgets from "
                + plan.filename() + (skipped.isEmpty() ? "" : "; skipped " + skipped.size() + " rows"));
        return new Report(replace, expensesDeleted, budgetsDeleted, expensesAdded, budgetsAdded, budgetsKept, skipped);
    }

    private static List<BudgetRow> readBudgets(String csv, List<String> fileErrors) {
        List<List<String>> rows = Csv.parse(csv);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = rows.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        int categoryColumn = header.indexOf("category");
        int limitColumn = header.indexOf("monthly_limit");
        if (categoryColumn < 0 || limitColumn < 0) {
            fileErrors.add("budgets.csv: the first row must name the columns category and monthly_limit");
            return List.of();
        }
        List<BudgetRow> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            List<String> errors = new ArrayList<>();
            String categoryText = categoryColumn < row.size() ? row.get(categoryColumn).trim() : "";
            String limitText = limitColumn < row.size() ? row.get(limitColumn).trim() : "";
            Category category = null;
            try {
                category = Category.valueOf(categoryText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("\"" + categoryText + "\" is not a category");
            }
            BigDecimal limit = CsvImportService.parseAmount(limitText).orElse(null);
            if (limit == null || limit.signum() <= 0 || limit.scale() > 2) {
                errors.add("\"" + limitText + "\" is not a valid monthly limit");
                limit = null;
            }
            result.add(new BudgetRow(i + 1, category, limit, List.copyOf(errors)));
        }
        return result;
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_FILE_BYTES) {
                throw new IOException("A file in the ZIP is larger than 5 MB");
            }
        }
        return out.toByteArray();
    }
}
