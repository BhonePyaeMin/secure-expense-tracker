package com.example.expenses.service;

import com.example.expenses.model.Expense;
import com.example.expenses.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public List<Expense> findAll() {
        return expenseRepository.findAllByOrderByDateDescIdDesc();
    }

    public Expense findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    @Transactional
    public Expense create(Expense expense) {
        Expense created = new Expense();
        copyFields(expense, created);
        return expenseRepository.save(created);
    }

    @Transactional
    public Expense update(Long id, Expense changes) {
        Expense existing = findById(id);
        copyFields(changes, existing);
        return existing; // saved on commit by JPA dirty checking
    }

    @Transactional
    public void delete(Long id) {
        expenseRepository.delete(findById(id));
    }

    private static void copyFields(Expense source, Expense target) {
        target.setTitle(source.getTitle() == null ? null : source.getTitle().trim());
        target.setAmount(source.getAmount());
        target.setCategory(source.getCategory());
        target.setDate(source.getDate());
        target.setNote(StringUtils.hasText(source.getNote()) ? source.getNote().trim() : null);
    }
}
