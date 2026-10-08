package com.example.expenses;

import com.example.expenses.dto.RecurringForm;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.RecurringExpense;
import com.example.expenses.model.User;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.RecurringExpenseRepository;
import com.example.expenses.repository.UserRepository;
import com.example.expenses.service.AuditService;
import com.example.expenses.service.RecurringExpenseNotFoundException;
import com.example.expenses.service.RecurringExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({RecurringExpenseService.class, AuditService.class})
class RecurringExpenseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Autowired
    private RecurringExpenseService service;

    @Autowired
    private RecurringExpenseRepository recurringRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("alice", "hash"));
    }

    @Test
    void firstDateTodayAddsOneExpenseNowAndSchedulesNextMonth() {
        int added = service.create(alice.getId(), form("Phone plan", "399.00", TODAY), TODAY);

        assertThat(added).isEqualTo(1);
        assertThat(expenseDates()).containsExactly(TODAY);
        assertThat(onlyRecurring().getNextDueDate()).isEqualTo(LocalDate.of(2026, 11, 8));
    }

    @Test
    void firstDateInTheFutureAddsNothingYet() {
        int added = service.create(alice.getId(), form("Gym", "900.00", LocalDate.of(2026, 10, 20)), TODAY);

        assertThat(added).isZero();
        assertThat(expenseDates()).isEmpty();
    }

    @Test
    void firstDateInThePastCatchesUpOnEveryMissedMonth() {
        int added = service.create(alice.getId(), form("Rent", "4500.00", LocalDate.of(2026, 7, 15)), TODAY);

        assertThat(added).isEqualTo(3);
        assertThat(expenseDates()).containsExactly(
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15));
        assertThat(onlyRecurring().getNextDueDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void dayThirtyOneUsesTheLastDayOfShorterMonthsThenGoesBack() {
        service.create(alice.getId(), form("Rent", "4500.00", LocalDate.of(2026, 1, 31)), LocalDate.of(2026, 4, 30));

        assertThat(expenseDates()).containsExactly(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));
        assertThat(onlyRecurring().getNextDueDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void scheduledRunAddsDueExpensesOnceEvenIfRunTwice() {
        service.create(alice.getId(), form("Spotify", "69.00", LocalDate.of(2026, 10, 20)), TODAY);

        assertThat(service.addDueExpenses(LocalDate.of(2026, 10, 20))).isEqualTo(1);
        assertThat(service.addDueExpenses(LocalDate.of(2026, 10, 20))).isZero();
        assertThat(expenseDates()).containsExactly(LocalDate.of(2026, 10, 20));
    }

    @Test
    void generatedExpensesBelongToTheOwnerAndAreMarkedRecurring() {
        service.create(alice.getId(), form("Netflix", "419.00", TODAY), TODAY);

        Expense expense = expenseRepository.findAll().get(0);
        assertThat(expense.getOwner().getId()).isEqualTo(alice.getId());
        assertThat(expense.getCategory()).isEqualTo(Category.FUN);
        assertThat(expense.getNote()).isEqualTo("Recurring");
    }

    @Test
    void pausedTemplatesAreSkippedAndResumingDoesNotBackfill() {
        service.create(alice.getId(), form("Gym", "900.00", LocalDate.of(2026, 10, 20)), TODAY);
        Long id = onlyRecurring().getId();

        service.pause(alice.getId(), id);
        assertThat(service.addDueExpenses(LocalDate.of(2026, 12, 25))).isZero();

        service.resume(alice.getId(), id, LocalDate.of(2026, 12, 25));
        assertThat(expenseDates()).isEmpty();
        assertThat(onlyRecurring().getNextDueDate()).isEqualTo(LocalDate.of(2027, 1, 20));
    }

    @Test
    void otherUsersCannotPauseOrDeleteYourRecurringExpense() {
        service.create(alice.getId(), form("Rent", "4500.00", LocalDate.of(2026, 10, 20)), TODAY);
        Long id = onlyRecurring().getId();
        User bob = userRepository.save(new User("bob", "hash"));

        assertThatThrownBy(() -> service.pause(bob.getId(), id)).isInstanceOf(RecurringExpenseNotFoundException.class);
        assertThatThrownBy(() -> service.delete(bob.getId(), id)).isInstanceOf(RecurringExpenseNotFoundException.class);
        assertThat(onlyRecurring().isActive()).isTrue();
    }

    @Test
    void deletingStopsFutureExpensesButKeepsPastOnes() {
        service.create(alice.getId(), form("Rent", "4500.00", LocalDate.of(2026, 9, 1)), TODAY);

        service.delete(alice.getId(), onlyRecurring().getId());

        assertThat(recurringRepository.findAll()).isEmpty();
        assertThat(expenseDates()).hasSize(2);
    }

    private RecurringForm form(String title, String amount, LocalDate firstDate) {
        RecurringForm form = new RecurringForm();
        form.setTitle(title);
        form.setAmount(new BigDecimal(amount));
        form.setCategory(title.equals("Netflix") || title.equals("Spotify") ? Category.FUN : Category.RENT);
        form.setFirstDate(firstDate);
        return form;
    }

    private RecurringExpense onlyRecurring() {
        List<RecurringExpense> all = recurringRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private List<LocalDate> expenseDates() {
        return expenseRepository.findAll().stream().map(Expense::getDate).sorted().toList();
    }
}
