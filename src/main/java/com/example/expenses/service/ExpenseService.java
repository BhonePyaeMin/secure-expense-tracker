package com.example.expenses.service;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.AuditAction;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.ExpenseSpecifications;
import com.example.expenses.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    @Transactional
    public Expense create(Long userId, ExpenseForm form) {
        Expense expense = new Expense();
        expense.setOwner(userRepository.getReferenceById(userId));
        copyFields(form, expense);
        Expense saved = expenseRepository.save(expense);
        auditService.record(userId, AuditAction.EXPENSE_CREATED, describe(saved));
        return saved;
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

        copyFields(form, existing); // saved on commit by JPA dirty checking
        if (!changes.isEmpty()) {
            auditService.record(userId, AuditAction.EXPENSE_UPDATED,
                    "#" + id + " " + existing.getTitle() + ": " + String.join("; ", changes));
        }
        return existing;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Expense expense = findOwned(userId, id);
        String description = describe(expense);
        expenseRepository.delete(expense);
        auditService.record(userId, AuditAction.EXPENSE_DELETED, description);
    }

    // Another user's expense id behaves exactly like a missing one (404), so ids can't be probed
    private Expense findOwned(Long userId, Long id) {
        return expenseRepository.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    private static void copyFields(ExpenseForm source, Expense target) {
        target.setTitle(cleanTitle(source));
        target.setAmount(source.getAmount());
        target.setCategory(source.getCategory());
        target.setDate(source.getDate());
        target.setNote(cleanNote(source));
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
        if (value instanceof String text) {
            return "\"" + text + "\"";
        }
        return value.toString();
    }
}
