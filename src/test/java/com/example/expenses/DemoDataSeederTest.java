package com.example.expenses;

import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.model.User;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("demo")
class DemoDataSeederTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void demoProfileCreatesDemoAccountWithSampleDataAndNoFutureDates() {
        User demo = userRepository.findByUsername("demo").orElseThrow();
        assertThat(expenseRepository.countByOwnerId(demo.getId())).isGreaterThan(20);
        assertThat(budgetRepository.findAllByOwnerIdOrderByCategoryAsc(demo.getId())).hasSize(3);
        assertThat(expenseRepository.findAll())
                .allSatisfy(expense -> assertThat(expense.getDate()).isBeforeOrEqualTo(LocalDate.now()));
    }
}
