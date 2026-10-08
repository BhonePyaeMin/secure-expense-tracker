package com.example.expenses.repository;

import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    /** One row per category: the database does the summing, so no expense rows are loaded. */
    @Query("""
            select new com.example.expenses.dto.CategoryTotal(e.category, sum(e.amount))
            from Expense e
            where e.date between :from and :to
            group by e.category
            """)
    List<CategoryTotal> totalsByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
