package com.example.expenses.repository;

import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.DailyTotal;
import com.example.expenses.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    /** Looks up an expense only if it belongs to this user, so other users' ids behave as "not found". */
    Optional<Expense> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    /** The most recent expense with this exact title, ignoring case (for pre-selecting its category). */
    Optional<Expense> findFirstByOwnerIdAndTitleIgnoreCaseOrderByDateDescIdDesc(Long ownerId, String title);

    /** One row per category: the database does the summing, so no expense rows are loaded. */
    @Query("""
            select new com.example.expenses.dto.CategoryTotal(e.category, sum(e.amount))
            from Expense e
            where e.owner.id = :ownerId and e.date between :from and :to
            group by e.category
            """)
    List<CategoryTotal> totalsByCategory(@Param("ownerId") Long ownerId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    /** One row per day that has spending, oldest first. */
    @Query("""
            select new com.example.expenses.dto.DailyTotal(e.date, sum(e.amount))
            from Expense e
            where e.owner.id = :ownerId and e.date between :from and :to
            group by e.date
            order by e.date
            """)
    List<DailyTotal> dailyTotals(@Param("ownerId") Long ownerId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
