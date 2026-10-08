package com.example.expenses.service;

import com.example.expenses.dto.IncomeForm;
import com.example.expenses.model.AuditAction;
import com.example.expenses.model.Income;
import com.example.expenses.repository.IncomeRepository;
import com.example.expenses.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public IncomeService(IncomeRepository incomeRepository, UserRepository userRepository, AuditService auditService) {
        this.incomeRepository = incomeRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<Income> findForMonth(Long userId, YearMonth month) {
        return incomeRepository.findAllByOwnerIdAndDateBetweenOrderByDateDescIdDesc(
                userId, month.atDay(1), month.atEndOfMonth());
    }

    public BigDecimal totalForMonth(Long userId, YearMonth month) {
        BigDecimal total = incomeRepository.totalBetween(userId, month.atDay(1), month.atEndOfMonth());
        return total == null ? BigDecimal.ZERO : total;
    }

    public List<String> previousSources(Long userId) {
        return incomeRepository.findSources(userId);
    }

    @Transactional
    public Income add(Long userId, IncomeForm form) {
        Income income = incomeRepository.save(new Income(userRepository.getReferenceById(userId),
                form.getAmount(), form.getSource().trim(), form.getDate()));
        auditService.record(userId, AuditAction.INCOME_ADDED, describe(income));
        return income;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Income income = incomeRepository.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new NotFoundException("Income", id));
        incomeRepository.delete(income);
        auditService.record(userId, AuditAction.INCOME_DELETED, describe(income));
    }

    private static String describe(Income income) {
        return "#" + income.getId() + " " + income.getSource() + " (" + income.getAmount().toPlainString()
                + ", " + income.getDate() + ")";
    }
}
