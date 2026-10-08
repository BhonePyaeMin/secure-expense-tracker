package com.example.expenses.service;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.AuditAction;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.PaymentMethod;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.ExpenseSpecifications;
import com.example.expenses.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Every method takes the logged-in user's id and only ever touches that user's expenses. */
@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ExpenseService(ExpenseRepository expenseRepository, UserRepository userRepository,
                          AuditService auditService) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public Page<Expense> search(Long userId, ExpenseFilter filter, Pageable pageable) {
        return expenseRepository.findAll(ExpenseSpecifications.matching(userId, filter), pageable);
    }

    /** Everything matching the filter, oldest first (used for export). */
    public List<Expense> findAll(Long userId, ExpenseFilter filter) {
        return expenseRepository.findAll(ExpenseSpecifications.matching(userId, filter),
                Sort.by(Sort.Order.asc("date"), Sort.Order.asc("id")));
    }

    public ExpenseForm formFor(Long userId, Long id) {
        return ExpenseForm.from(findOwned(userId, id));
    }

    /** The category used the last time this title was entered, ignoring case. */
    public Optional<Category> lastCategoryFor(Long userId, String title) {
        if (!StringUtils.hasText(title)) {
            return Optional.empty();
        }
        return expenseRepository.findFirstByOwnerIdAndDeletedAtIsNullAndTitleIgnoreCaseOrderByDateDescIdDesc(
                        userId, title.trim())
                .map(Expense::getCategory);
    }

    /** Adds a copy of an expense dated today, for things bought again (coffee, bus fare). */
    @Transactional
    public Expense repeat(Long userId, Long id, LocalDate today) {
        Expense original = findOwned(userId, id);
        Expense copy = new Expense(original.getOwner(), original.getTitle(),
                original.getAmount(), original.getCategory(), today, original.getNote());
        copy.setPaymentMethod(original.getPaymentMethod());
        expenseRepository.save(copy);
        auditService.record(userId, AuditAction.EXPENSE_CREATED, describe(copy) + ", repeated from #" + id);
        return copy;
    }

    @Transactional
    public Expense create(Long userId, ExpenseForm form) {
        Expense expense = new Expense();
        expense.setOwner(userRepository.getReferenceById(userId));
        copyFields(form, expense);
        Expense saved = expenseRepository.save(expense);
        auditService.record(userId, AuditAction.EXPENSE_CREATED, describe(saved));
        return saved;
    }

    /** Saves all imported expenses in one transaction: either every row is saved or none is. */
    @Transactional
    public int importAll(Long userId, List<ExpenseForm> forms, String filename) {
        for (ExpenseForm form : forms) {
            Expense expense = new Expense();
            expense.setOwner(userRepository.getReferenceById(userId));
            copyFields(form, expense);
            expenseRepository.save(expense);
        }
        auditService.record(userId, AuditAction.EXPENSES_IMPORTED,
                forms.size() + " expenses from " + (StringUtils.hasText(filename) ? filename : "a CSV file"));
        return forms.size();
    }

    @Transactional
    public Expense update(Long userId, Long id, ExpenseForm form) {
        Expense existing = findOwned(userId, id);
        List<String> changes = new ArrayList<>();
        addChange(changes, "title", existing.getTitle(), cleanTitle(form));
        addChange(changes, "amount", existing.getAmount(), form.getAmount());
        addChange(changes, "category", existing.getCategory(), form.getCategory());
        addChange(changes, "date", existing.getDate(), form.getDate());
        addChange(changes, "note", existing.getNote(), cleanNote(form));
        addChange(changes, "paid with", existing.getPaymentMethod(), form.getPaymentMethod());

        if (!Objects.equals(existing.getDate(), form.getDate())) {
            existing.setRecurringExpenseId(null); // moved to another day: no longer that month's occurrence
        }
        copyFields(form, existing); // saved on commit by JPA dirty checking
        if (!changes.isEmpty()) {
            auditService.record(userId, AuditAction.EXPENSE_UPDATED,
                    "#" + id + " " + existing.getTitle() + ": " + String.join("; ", changes));
        }
        return existing;
    }

    /** Moves the expense to the trash. It can be restored until it's deleted forever. */
    @Transactional
    public void delete(Long userId, Long id) {
        Expense expense = findOwned(userId, id);
        expense.moveToTrash(Instant.now());
        auditService.record(userId, AuditAction.EXPENSE_DELETED, describe(expense));
    }

    public Page<Expense> trash(Long userId, int page) {
        return expenseRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDescIdDesc(
                userId, PageRequest.of(Math.max(page, 0), 20));
    }

    public long trashCount(Long userId) {
        return expenseRepository.countByOwnerIdAndDeletedAtIsNotNull(userId);
    }

    @Transactional
    public Expense restore(Long userId, Long id) {
        Expense expense = findInTrash(userId, id);
        expense.restore();
        auditService.record(userId, AuditAction.EXPENSE_RESTORED, describe(expense));
        return expense;
    }

    /** Removes an expense for good. Only possible from the trash. */
    @Transactional
    public void deleteForever(Long userId, Long id) {
        Expense expense = findInTrash(userId, id);
        String description = describe(expense);
        expenseRepository.delete(expense);
        auditService.record(userId, AuditAction.EXPENSE_PURGED, description);
    }

    // Another user's expense id behaves exactly like a missing one (404), so ids can't be probed.
    // Expenses in the trash count as missing too, so they can't be edited or repeated.
    private Expense findOwned(Long userId, Long id) {
        return expenseRepository.findByIdAndOwnerIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    private Expense findInTrash(Long userId, Long id) {
        return expenseRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(id, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    private static void copyFields(ExpenseForm source, Expense target) {
        target.setTitle(cleanTitle(source));
        target.setAmount(source.getAmount());
        target.setCategory(source.getCategory());
        target.setDate(source.getDate());
        target.setNote(cleanNote(source));
        target.setPaymentMethod(source.getPaymentMethod());
    }

    private static String cleanTitle(ExpenseForm form) {
        return form.getTitle().trim();
    }

    private static String cleanNote(ExpenseForm form) {
        return StringUtils.hasText(form.getNote()) ? form.getNote().trim() : null;
    }

    private static String describe(Expense expense) {
        return "#" + expense.getId() + " " + expense.getTitle() + " (" + expense.getAmount().toPlainString()
                + ", " + expense.getCategory().getLabel() + ", " + expense.getDate() + ")";
    }

    private static void addChange(List<String> changes, String field, Object before, Object after) {
        boolean same = before instanceof BigDecimal b && after instanceof BigDecimal a
                ? b.compareTo(a) == 0
                : Objects.equals(before, after);
        if (!same) {
            changes.add(field + " " + display(before) + " → " + display(after));
        }
    }

    private static String display(Object value) {
        if (value == null) {
            return "(empty)";
        }
        if (value instanceof BigDecimal amount) {
            return amount.toPlainString();
        }
        if (value instanceof Category category) {
            return category.getLabel();
        }
        if (value instanceof PaymentMethod method) {
            return method.getLabel();
        }
        if (value instanceof String text) {
            return "\"" + text + "\"";
        }
        return value.toString();
    }
}
