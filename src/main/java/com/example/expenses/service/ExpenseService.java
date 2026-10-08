package com.example.expenses.service;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.Expense;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.ExpenseSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public Page<Expense> search(ExpenseFilter filter, Pageable pageable) {
        return expenseRepository.findAll(ExpenseSpecifications.matching(filter), pageable);
    }

    public Expense findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    public ExpenseForm formFor(Long id) {
        return ExpenseForm.from(findById(id));
    }

    @Transactional
    public Expense create(ExpenseForm form) {
        Expense expense = new Expense();
        copyFields(form, expense);
        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense update(Long id, ExpenseForm form) {
        Expense existing = findById(id);
        copyFields(form, existing);
        return existing; // saved on commit by JPA dirty checking
    }

    @Transactional
    public void delete(Long id) {
        expenseRepository.delete(findById(id));
    }

    private static void copyFields(ExpenseForm source, Expense target) {
        target.setTitle(source.getTitle().trim());
        target.setAmount(source.getAmount());
        target.setCategory(source.getCategory());
        target.setDate(source.getDate());
        target.setNote(StringUtils.hasText(source.getNote()) ? source.getNote().trim() : null);
    }
}
