package com.example.expenses.repository;

import com.example.expenses.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findAllByOwnerIdOrderByCreatedAtAscIdAsc(Long ownerId);

    Optional<SavingsGoal> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);
}
