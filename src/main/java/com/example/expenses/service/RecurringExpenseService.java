package com.example.expenses.service;

import com.example.expenses.dto.RecurringForm;
import com.example.expenses.model.AuditAction;
import com.example.expenses.model.Expense;
import com.example.expenses.model.RecurringExpense;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.RecurringExpenseRepository;
import com.example.expenses.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RecurringExpenseService {

    // Safety net so a bad date can never create hundreds of expenses in one go
    static final int MAX_CATCH_UP_PER_RUN = 24;

    private final RecurringExpenseRepository recurringRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RecurringExpenseService(RecurringExpenseRepository recurringRepository, ExpenseRepository expenseRepository,
                                   UserRepository userRepository, AuditService auditService) {
        this.recurringRepository = recurringRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<RecurringExpense> findAll(Long userId) {
        return recurringRepository.findAllByOwnerIdOrderByNextDueDateAscIdAsc(userId);
    }

    /**
     * Saves the template and immediately adds any expenses already due (a first date in the past).
     *
     * @return how many expenses were added now
     */
    @Transactional
    public int create(Long userId, RecurringForm form, LocalDate today) {
        String note = StringUtils.hasText(form.getNote()) ? form.getNote().trim() : null;
        RecurringExpense recurring = recurringRepository.save(new RecurringExpense(
                userRepository.getReferenceById(userId), form.getTitle().trim(), form.getAmount(),
                form.getCategory(), note, form.getFirstDate()));
        auditService.record(userId, AuditAction.RECURRING_CREATED, recurring.getTitle() + " ("
                + recurring.getAmount().toPlainString() + ") every month on day " + recurring.getDayOfMonth()
                + ", starting " + recurring.getNextDueDate());
        return addDueExpenses(recurring, today);
    }

    @Transactional
    public void pause(Long userId, Long id) {
        RecurringExpense recurring = findOwned(userId, id);
        recurring.pause();
        auditService.record(userId, AuditAction.RECURRING_PAUSED, recurring.getTitle());
    }

    @Transactional
    public int resume(Long userId, Long id, LocalDate today) {
        RecurringExpense recurring = findOwned(userId, id);
        recurring.resume(today);
        auditService.record(userId, AuditAction.RECURRING_RESUMED,
                recurring.getTitle() + ", next on " + recurring.getNextDueDate());
        return addDueExpenses(recurring, today);
    }

    /** Stops future expenses. Expenses already added stay. */
    @Transactional
    public void delete(Long userId, Long id) {
        RecurringExpense recurring = findOwned(userId, id);
        recurringRepository.delete(recurring);
        auditService.record(userId, AuditAction.RECURRING_DELETED, recurring.getTitle());
    }

    /**
     * Adds an expense for every due date up to and including today, for all users.
     * Run daily by {@code RecurringExpenseScheduler} and once at startup. Safe to run repeatedly.
     */
    @Transactional
    public int addDueExpenses(LocalDate today) {
        int added = 0;
        for (RecurringExpense recurring : recurringRepository.findAllByActiveTrueAndNextDueDateLessThanEqual(today)) {
            added += addDueExpenses(recurring, today);
        }
        return added;
    }

    private int addDueExpenses(RecurringExpense recurring, LocalDate today) {
        int added = 0;
        while (recurring.isDue(today) && added < MAX_CATCH_UP_PER_RUN) {
            Expense expense = expenseRepository.save(new Expense(recurring.getOwner(), recurring.getTitle(),
                    recurring.getAmount(), recurring.getCategory(), recurring.getNextDueDate(), noteFor(recurring)));
            auditService.record(recurring.getOwner().getId(), AuditAction.RECURRING_ADDED, "#" + expense.getId() + " "
                    + expense.getTitle() + " (" + expense.getAmount().toPlainString() + ", " + expense.getDate() + ")");
            recurring.advance();
            added++;
        }
        return added;
    }

    private static String noteFor(RecurringExpense recurring) {
        return recurring.getNote() == null ? "Recurring" : "Recurring: " + recurring.getNote();
    }

    private RecurringExpense findOwned(Long userId, Long id) {
        return recurringRepository.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new RecurringExpenseNotFoundException(id));
    }
}
