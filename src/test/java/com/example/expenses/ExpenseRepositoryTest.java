package com.example.expenses;

import com.example.expenses.dto.CategoryTotal;
import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.User;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.ExpenseSpecifications;
import com.example.expenses.repository.UserRepository;
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
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
class ExpenseRepositoryTest {

    private static final PageRequest FIRST_PAGE =
            PageRequest.of(0, 10, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));

    @Autowired
    private ExpenseRepository repository;

    @Autowired
    private UserRepository userRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner", "hash"));
        User someoneElse = userRepository.save(new User("someone-else", "hash"));
        repository.save(new Expense(someoneElse, "Not mine", new BigDecimal("999.00"), Category.FOOD,
                LocalDate.parse("2026-10-10"), null));
        repository.saveAll(List.of(
                expense("Last day of September", "20.00", Category.FOOD, "2026-09-30"),
                expense("First day of October", "12.50", Category.FOOD, "2026-10-01"),
                expense("Rent", "300.00", Category.RENT, "2026-10-15"),
                expense("Last day of October", "2.40", Category.TRANSPORT, "2026-10-31"),
                expense("First day of November", "3.00", Category.FOOD, "2026-11-01")));
    }

    @Test
    void otherUsersExpensesAreNeverIncluded() {
        Page<Expense> page = search(new ExpenseFilter(null, null));

        assertThat(page.getContent()).extracting(Expense::getTitle).doesNotContain("Not mine");
        assertThat(repository.findByIdAndOwnerId(page.getContent().get(0).getId(), owner.getId())).isPresent();
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
                ExpenseSpecifications.matching(owner.getId(), new ExpenseFilter(null, null)), PageRequest.of(1, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    void totalsByCategorySumsOnlyTheGivenMonth() {
        repository.save(expense("Second October lunch", "7.50", Category.FOOD, "2026-10-20"));

        List<CategoryTotal> totals = repository.totalsByCategory(owner.getId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(totals).extracting(CategoryTotal::category, t -> t.total().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder(
                        tuple(Category.FOOD, "20"),
                        tuple(Category.RENT, "300"),
                        tuple(Category.TRANSPORT, "2.4"));
    }

    private Page<Expense> search(ExpenseFilter filter) {
        return repository.findAll(ExpenseSpecifications.matching(owner.getId(), filter), FIRST_PAGE);
    }

    private Expense expense(String title, String amount, Category category, String date) {
        return new Expense(owner, title, new BigDecimal(amount), category, LocalDate.parse(date), null);
    }
}
