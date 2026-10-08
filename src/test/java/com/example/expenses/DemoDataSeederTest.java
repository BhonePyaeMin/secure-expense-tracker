package com.example.expenses;

import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
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

    @Test
    void demoProfileSeedsSampleDataWithNoFutureDates() {
        assertThat(expenseRepository.count()).isGreaterThan(20);
        assertThat(budgetRepository.count()).isEqualTo(3);
        assertThat(expenseRepository.findAll())
                .allSatisfy(expense -> assertThat(expense.getDate()).isBeforeOrEqualTo(LocalDate.now()));
    }
}
