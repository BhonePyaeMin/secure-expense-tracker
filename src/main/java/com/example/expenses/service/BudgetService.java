package com.example.expenses.service;

import com.example.expenses.model.AuditAction;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public BudgetService(BudgetRepository budgetRepository, UserRepository userRepository,
                         AuditService auditService) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<Budget> findAll(Long userId) {
        return budgetRepository.findAllByOwnerIdOrderByCategoryAsc(userId);
    }

    /** Creates the budget for this category, or updates its limit if one exists. */
    @Transactional
    public void setLimit(Long userId, Category category, BigDecimal monthlyLimit) {
        Optional<Budget> existing = budgetRepository.findByOwnerIdAndCategory(userId, category);
        String details;
        if (existing.isPresent()) {
            Budget budget = existing.get();
            details = category.getLabel() + ": " + budget.getMonthlyLimit().toPlainString()
                    + " → " + monthlyLimit.toPlainString();
            budget.setMonthlyLimit(monthlyLimit);
        } else {
            budgetRepository.save(new Budget(userRepository.getReferenceById(userId), category, monthlyLimit));
            details = category.getLabel() + ": " + monthlyLimit.toPlainString();
        }
        auditService.record(userId, AuditAction.BUDGET_SET, details);
    }

    @Transactional
    public void remove(Long userId, Category category) {
        budgetRepository.findByOwnerIdAndCategory(userId, category).ifPresent(budget -> {
            budgetRepository.delete(budget);
            auditService.record(userId, AuditAction.BUDGET_REMOVED,
                    category.getLabel() + ": " + budget.getMonthlyLimit().toPlainString());
        });
    }
}
