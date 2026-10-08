package com.example.expenses.repository;

import com.example.expenses.model.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    List<RecurringExpense> findAllByOwnerIdOrderByNextDueDateAscIdAsc(Long ownerId);

    Optional<RecurringExpense> findByIdAndOwnerId(Long id, Long ownerId);

    /** Everything due today or earlier, across all users (for the scheduled job). */
    List<RecurringExpense> findAllByActiveTrueAndNextDueDateLessThanEqual(LocalDate date);
}
