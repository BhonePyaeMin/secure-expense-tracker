package com.example.expenses.service;

import com.example.expenses.model.AuditAction;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Expense;
import com.example.expenses.model.Income;
import com.example.expenses.model.RecurringExpense;
import com.example.expenses.model.SavingsGoal;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.IncomeRepository;
import com.example.expenses.repository.RecurringExpenseRepository;
import com.example.expenses.repository.SavingsGoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A download of one user's own data: a ZIP with one CSV file per kind of data. It never contains
 * other users' data or any password hashes, which a full database dump would.
 * expenses.csv uses the import format, so it can be imported again (extra columns are ignored).
 */
@Service
public class BackupService {

    private static final String LINE_END = "\r\n";

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final BudgetRepository budgetRepository;
    private final RecurringExpenseRepository recurringRepository;
    private final SavingsGoalRepository goalRepository;
    private final AuditService auditService;

    public BackupService(ExpenseRepository expenseRepository, IncomeRepository incomeRepository,
                         BudgetRepository budgetRepository, RecurringExpenseRepository recurringRepository,
                         SavingsGoalRepository goalRepository, AuditService auditService) {
        this.goalRepository = goalRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.budgetRepository = budgetRepository;
        this.recurringRepository = recurringRepository;
        this.auditService = auditService;
    }

    /** Streams the ZIP straight to {@code out}, one file at a time, so memory use stays small. */
    @Transactional
    public void writeZip(Long userId, String filename, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);

        startFile(zip, writer, "expenses.csv",
                "id,title,amount,category,date,note,payment_method,deleted_at,recurring_expense_id");
        for (Expense e : expenseRepository.findAllByOwnerIdOrderByDateAscIdAsc(userId)) {
            row(writer, e.getId(), Csv.escape(e.getTitle()), e.getAmount().toPlainString(), e.getCategory().name(),
                    e.getDate(), Csv.escape(e.getNote()), e.getPaymentMethod() == null ? "" : e.getPaymentMethod().name(),
                    e.getDeletedAt() == null ? "" : e.getDeletedAt(), orEmpty(e.getRecurringExpenseId()));
        }
        endFile(zip, writer);

        startFile(zip, writer, "income.csv", "id,source,amount,date");
        for (Income i : incomeRepository.findAllByOwnerIdOrderByDateAscIdAsc(userId)) {
            row(writer, i.getId(), Csv.escape(i.getSource()), i.getAmount().toPlainString(), i.getDate());
        }
        endFile(zip, writer);

        startFile(zip, writer, "budgets.csv", "category,monthly_limit");
        for (Budget b : budgetRepository.findAllByOwnerIdOrderByCategoryAsc(userId)) {
            row(writer, b.getCategory().name(), b.getMonthlyLimit().toPlainString());
        }
        endFile(zip, writer);

        startFile(zip, writer, "recurring.csv",
                "id,title,amount,category,day_of_month,next_due_date,active,payment_method,note");
        List<RecurringExpense> recurring = recurringRepository.findAllByOwnerIdOrderByNextDueDateAscIdAsc(userId);
        for (RecurringExpense r : recurring) {
            row(writer, r.getId(), Csv.escape(r.getTitle()), r.getAmount().toPlainString(), r.getCategory().name(),
                    r.getDayOfMonth(), r.getNextDueDate(), r.isActive(),
                    r.getPaymentMethod() == null ? "" : r.getPaymentMethod().name(), Csv.escape(r.getNote()));
        }
        endFile(zip, writer);

        startFile(zip, writer, "goals.csv", "id,name,target_amount,saved_amount,target_date");
        for (SavingsGoal g : goalRepository.findAllByOwnerIdOrderByCreatedAtAscIdAsc(userId)) {
            row(writer, g.getId(), Csv.escape(g.getName()), g.getTargetAmount().toPlainString(),
                    g.getSavedAmount().toPlainString(), orEmpty(g.getTargetDate()));
        }
        endFile(zip, writer);

        zip.finish();
        auditService.record(userId, AuditAction.BACKUP_DOWNLOADED, filename);
    }

    private static void startFile(ZipOutputStream zip, Writer writer, String name, String header) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        writer.write('\uFEFF'); // so Excel reads the file as UTF-8
        writer.write(header);
        writer.write(LINE_END);
    }

    private static void endFile(ZipOutputStream zip, Writer writer) throws IOException {
        writer.flush();
        zip.closeEntry();
    }

    private static void row(Writer writer, Object... cells) throws IOException {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(String.valueOf(cells[i]));
        }
        writer.write(LINE_END);
    }

    private static Object orEmpty(Object value) {
        return value == null ? "" : value;
    }
}
