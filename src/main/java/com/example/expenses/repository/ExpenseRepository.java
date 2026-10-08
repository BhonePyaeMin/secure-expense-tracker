package com.example.expenses.repository;

import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.DailyTotal;
import com.example.expenses.dto.PaymentMethodTotal;
import com.example.expenses.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    /** Looks up an expense only if it belongs to this user, so other users' ids behave as "not found". */
    Optional<Expense> findByIdAndOwnerId(Long id, Long ownerId);

    /** Like findByIdAndOwnerId, but only if it's not in the trash. */
    Optional<Expense> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    /** Only if it is in the trash (for restore and delete forever). */
    Optional<Expense> findByIdAndOwnerIdAndDeletedAtIsNotNull(Long id, Long ownerId);

    Page<Expense> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDescIdDesc(Long ownerId, Pageable pageable);

    long countByOwnerIdAndDeletedAtIsNotNull(Long ownerId);

    long countByOwnerId(Long ownerId);

    /** Removes all of a user's expenses, including the trash (restore in "replace" mode). */
    long deleteByOwnerId(Long ownerId);

    /** Everything, including the trash (for backups). */
    List<Expense> findAllByOwnerIdOrderByDateAscIdAsc(Long ownerId);

    boolean existsByRecurringExpenseIdAndDate(Long recurringExpenseId, LocalDate date);

    /** The most recent expense with this exact title, ignoring case (for pre-selecting its category). */
    Optional<Expense> findFirstByOwnerIdAndDeletedAtIsNullAndTitleIgnoreCaseOrderByDateDescIdDesc(Long ownerId,
                                                                                               String title);

    /** One row per category: the database does the summing, so no expense rows are loaded. */
    @Query("""
            select new com.example.expenses.dto.CategoryTotal(e.category, sum(e.amount))
            from Expense e
            where e.owner.id = :ownerId and e.date between :from and :to and e.deletedAt is null
            group by e.category
            """)
    List<CategoryTotal> totalsByCategory(@Param("ownerId") Long ownerId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    /** The biggest expenses in the date range, leaving out the trash. */
    List<Expense> findTop5ByOwnerIdAndDeletedAtIsNullAndDateBetweenOrderByAmountDescDateDescIdDesc(
            Long ownerId, LocalDate from, LocalDate to);

    /** Total spent in the date range (null when nothing), leaving out the trash. */
    @Query("""
            select sum(e.amount) from Expense e
            where e.owner.id = :ownerId and e.date between :from and :to and e.deletedAt is null
            """)
    BigDecimal totalBetween(@Param("ownerId") Long ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The part of totalBetween that recurring expenses added (rent, subscriptions). */
    @Query("""
            select sum(e.amount) from Expense e
            where e.owner.id = :ownerId and e.date between :from and :to and e.deletedAt is null
              and e.recurringExpenseId is not null
            """)
    BigDecimal recurringTotalBetween(@Param("ownerId") Long ownerId, @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** One row per payment method used (null = not set), biggest first. */
    @Query("""
            select new com.example.expenses.dto.PaymentMethodTotal(e.paymentMethod, sum(e.amount))
            from Expense e
            where e.owner.id = :ownerId and e.date between :from and :to and e.deletedAt is null
            group by e.paymentMethod
            order by sum(e.amount) desc
            """)
    List<PaymentMethodTotal> totalsByPaymentMethod(@Param("ownerId") Long ownerId,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    /** One row per day that has spending, oldest first. */
    @Query("""
            select new com.example.expenses.dto.DailyTotal(e.date, sum(e.amount))
            from Expense e
            where e.owner.id = :ownerId and e.date between :from and :to and e.deletedAt is null
            group by e.date
            order by e.date
            """)
    List<DailyTotal> dailyTotals(@Param("ownerId") Long ownerId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
