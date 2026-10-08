package com.example.expenses.service;

import com.example.expenses.dto.GoalForm;
import com.example.expenses.model.AuditAction;
import com.example.expenses.model.SavingsGoal;
import com.example.expenses.repository.SavingsGoalRepository;
import com.example.expenses.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SavingsGoalService {

    private final SavingsGoalRepository goalRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SavingsGoalService(SavingsGoalRepository goalRepository, UserRepository userRepository,
                              AuditService auditService) {
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<SavingsGoal> findAll(Long userId) {
        return goalRepository.findAllByOwnerIdOrderByCreatedAtAscIdAsc(userId);
    }

    @Transactional
    public SavingsGoal create(Long userId, GoalForm form) {
        BigDecimal saved = form.getAlreadySaved() == null ? BigDecimal.ZERO : form.getAlreadySaved();
        SavingsGoal goal = goalRepository.save(new SavingsGoal(userRepository.getReferenceById(userId),
                form.getName().trim(), form.getTargetAmount(), saved, form.getTargetDate()));
        auditService.record(userId, AuditAction.GOAL_CREATED, goal.getName() + ": target "
                + goal.getTargetAmount().toPlainString() + (goal.getTargetDate() == null ? "" : " by " + goal.getTargetDate()));
        return goal;
    }

    @Transactional
    public SavingsGoal deposit(Long userId, Long id, BigDecimal amount) {
        SavingsGoal goal = findOwned(userId, id);
        goal.deposit(amount);
        auditService.record(userId, AuditAction.GOAL_DEPOSIT, describe(goal, amount));
        return goal;
    }

    /** @throws IllegalArgumentException when taking out more than has been saved */
    @Transactional
    public SavingsGoal withdraw(Long userId, Long id, BigDecimal amount) {
        SavingsGoal goal = findOwned(userId, id);
        goal.withdraw(amount);
        auditService.record(userId, AuditAction.GOAL_WITHDRAWAL, describe(goal, amount));
        return goal;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        SavingsGoal goal = findOwned(userId, id);
        goalRepository.delete(goal);
        auditService.record(userId, AuditAction.GOAL_DELETED,
                goal.getName() + " (" + goal.getSavedAmount().toPlainString() + " saved)");
    }

    private SavingsGoal findOwned(Long userId, Long id) {
        return goalRepository.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new NotFoundException("Savings goal", id));
    }

    private static String describe(SavingsGoal goal, BigDecimal amount) {
        return goal.getName() + ": " + amount.toPlainString() + ", now " + goal.getSavedAmount().toPlainString()
                + " of " + goal.getTargetAmount().toPlainString();
    }
}
