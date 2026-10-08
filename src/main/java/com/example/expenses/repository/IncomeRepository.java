package com.example.expenses.repository;

import com.example.expenses.model.Income;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findAllByOwnerIdAndDateBetweenOrderByDateDescIdDesc(Long ownerId, LocalDate from, LocalDate to);

    List<Income> findAllByOwnerIdOrderByDateAscIdAsc(Long ownerId);

    Optional<Income> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    /** Sum of income in the date range, or null when there is none. */
    @Query("""
            select sum(i.amount)
            from Income i
            where i.owner.id = :ownerId and i.date between :from and :to
            """)
    BigDecimal totalBetween(@Param("ownerId") Long ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Sources used before, offered as suggestions in the form. */
    @Query("select distinct i.source from Income i where i.owner.id = :ownerId order by i.source")
    List<String> findSources(@Param("ownerId") Long ownerId);
}
