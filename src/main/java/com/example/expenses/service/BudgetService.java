package com.example.expenses.service;

import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.repository.BudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public List<Budget> findAll() {
        return budgetRepository.findAllByOrderByCategoryAsc();
    }

    /** Creates the budget for this category, or updates its limit if one exists. */
    @Transactional
    public void setLimit(Category category, BigDecimal monthlyLimit) {
        budgetRepository.findByCategory(category)
                .ifPresentOrElse(
                        budget -> budget.setMonthlyLimit(monthlyLimit),
                        () -> budgetRepository.save(new Budget(category, monthlyLimit)));
    }

    @Transactional
    public void remove(Category category) {
        budgetRepository.findByCategory(category).ifPresent(budgetRepository::delete);
    }
}
