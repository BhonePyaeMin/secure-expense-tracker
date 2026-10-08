package com.example.expenses.repository;

import com.example.expenses.model.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    List<RecurringExpense> findAllByOwnerIdOrderByNextDueDateAscIdAsc(Long ownerId);

    Optional<RecurringExpense> findByIdAndOwnerId(Long id, Long ownerId);

    /** Sum of active recurring expenses due after {@code after} up to {@code until} (null when none). */
    @Query("""
            select sum(r.amount) from RecurringExpense r
            where r.owner.id = :ownerId and r.active = true and r.nextDueDate > :after and r.nextDueDate <= :until
            """)
    BigDecimal upcomingTotal(@Param("ownerId") Long ownerId, @Param("after") LocalDate after,
                             @Param("until") LocalDate until);

    /** Everything due today or earlier, across all users (for the scheduled job). */
    List<RecurringExpense> findAllByActiveTrueAndNextDueDateLessThanEqual(LocalDate date);
}
