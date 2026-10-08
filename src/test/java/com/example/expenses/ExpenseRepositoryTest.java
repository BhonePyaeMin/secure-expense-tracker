package com.example.expenses;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.ExpenseSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ExpenseRepositoryTest {

    private static final PageRequest FIRST_PAGE =
            PageRequest.of(0, 10, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));

    @Autowired
    private ExpenseRepository repository;

    @BeforeEach
    void setUp() {
        repository.saveAll(List.of(
                expense("Last day of September", "20.00", Category.FOOD, "2026-09-30"),
                expense("First day of October", "12.50", Category.FOOD, "2026-10-01"),
                expense("Rent", "300.00", Category.RENT, "2026-10-15"),
                expense("Last day of October", "2.40", Category.TRANSPORT, "2026-10-31"),
                expense("First day of November", "3.00", Category.FOOD, "2026-11-01")));
    }

    @Test
    void monthFilterIncludesFirstAndLastDayOnly() {
        Page<Expense> page = search(new ExpenseFilter(YearMonth.of(2026, 10), null));

        assertThat(page.getContent()).extracting(Expense::getTitle)
                .containsExactly("Last day of October", "Rent", "First day of October");
    }

    @Test
    void monthAndCategoryFilterCombine() {
        Page<Expense> page = search(new ExpenseFilter(YearMonth.of(2026, 10), Category.FOOD));

        assertThat(page.getContent()).extracting(Expense::getTitle)
                .containsExactly("First day of October");
    }

    @Test
    void categoryFilterWorksWithoutMonth() {
        Page<Expense> page = search(new ExpenseFilter(null, Category.FOOD));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void emptyFilterReturnsEverythingNewestFirst() {
        Page<Expense> page = search(new ExpenseFilter(null, null));

        assertThat(page.getContent()).extracting(Expense::getTitle)
                .first().isEqualTo("First day of November");
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void resultsArePaginated() {
        Page<Expense> page = repository.findAll(
                ExpenseSpecifications.matching(new ExpenseFilter(null, null)), PageRequest.of(1, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    private Page<Expense> search(ExpenseFilter filter) {
        return repository.findAll(ExpenseSpecifications.matching(filter), FIRST_PAGE);
    }

    private static Expense expense(String title, String amount, Category category, String date) {
        return new Expense(title, new BigDecimal(amount), category, LocalDate.parse(date), null);
    }
}
