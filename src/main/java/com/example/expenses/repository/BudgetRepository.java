package com.example.expenses.repository;

import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByCategory(Category category);

    List<Budget> findAllByOrderByCategoryAsc();
}
