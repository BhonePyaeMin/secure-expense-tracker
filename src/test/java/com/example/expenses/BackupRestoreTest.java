package com.example.expenses;

import com.example.expenses.config.TimeConfig;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.PaymentMethod;
import com.example.expenses.model.User;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.UserRepository;
import com.example.expenses.security.AppUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Backup, wipe, restore: the data must come back identical. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackupRestoreTest {

    private static final LocalDate DAY = LocalDate.now(TimeConfig.ZONE).minusDays(3);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    private User alice;
    private AppUserDetails signedIn;

    /** What must survive a round trip, for comparing before and after. */
    record Snapshot(String title, BigDecimal amount, Category category, LocalDate date, String note,
                    PaymentMethod paymentMethod, Instant deletedAt) {
    }

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("alice", "hash"));
        signedIn = new AppUserDetails(alice.getId(), "alice", "unused", false);

        save("Lunch, with \"friends\"", "85.50", Category.FOOD, DAY, "Line one\nline two", PaymentMethod.PROMPTPAY, null);
        save("=SUM(A1:A3)", "0.01", Category.OTHER, DAY.minusDays(1), null, null, null);
        save("ข้าวมันไก่", "50.00", Category.FOOD, DAY.minusDays(2), "ร้านป้า, ตลาด", PaymentMethod.CASH, null);
        save("Old gadget", "990.00", Category.OTHER, DAY.minusDays(5), "-returned", PaymentMethod.CARD,
                Instant.parse("2026-09-30T10:15:30Z"));
        save("Rent", "4500.00", Category.RENT, DAY.minusDays(20), "Dorm, room 304", PaymentMethod.BANK, null);
        budgetRepository.save(new Budget(alice, Category.FOOD, new BigDecimal("3000.00")));
        budgetRepository.save(new Budget(alice, Category.FUN, new BigDecimal("500.50")));
    }

    @Test
    void backupThenWipeThenRestoreGivesIdenticalData() throws Exception {
        List<Snapshot> expensesBefore = expenses();
        List<String> budgetsBefore = budgets();

        byte[] backup = mockMvc.perform(get("/backup").with(user(signedIn)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        // Wipe
        expenseRepository.deleteByOwnerId(alice.getId());
        budgetRepository.deleteByOwnerId(alice.getId());
        assertThat(expenses()).isEmpty();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(multipart("/restore").file(new MockMultipartFile("file", "backup.zip", "application/zip", backup))
                        .session(session).with(user(signedIn)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nothing has been saved yet")))
                .andExpect(content().string(containsString("OK, goes to the trash")));
        assertThat(expenses()).isEmpty(); // the preview saves nothing

        mockMvc.perform(post("/restore/confirm").param("mode", "add")
                        .session(session).with(user(signedIn)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Added 5 expenses.")))
                .andExpect(content().string(containsString("Skipped 0 rows")));

        assertThat(expenses()).isEqualTo(expensesBefore);
        assertThat(budgets()).isEqualTo(budgetsBefore);
    }

    @Test
    void addModeKeepsExistingDataAndExistingBudgets() throws Exception {
        MockHttpSession session = previewOf("expenses.csv", "title,amount,date,category\nCoffee,55.00," + DAY + ",FOOD\n");

        mockMvc.perform(post("/restore/confirm").session(session).with(user(signedIn)).with(csrf()))
                .andExpect(content().string(containsString("Added 1 expenses.")));

        assertThat(expenses()).hasSize(6);
        assertThat(budgets()).hasSize(2);
    }

    @Test
    void replaceNeedsTheConfirmationTickAndOnlyTouchesYourOwnData() throws Exception {
        User bob = userRepository.save(new User("bob", "hash"));
        expenseRepository.save(new Expense(bob, "Bob lunch", new BigDecimal("60.00"), Category.FOOD, DAY, null));
        MockHttpSession session = previewOf("expenses.csv", "title,amount,date\nCoffee,55.00," + DAY + "\n");

        mockMvc.perform(post("/restore/confirm").param("mode", "replace").session(session).with(user(signedIn)).with(csrf()))
                .andExpect(content().string(containsString("tick the box")));
        assertThat(expenses()).hasSize(5); // nothing deleted without the tick

        mockMvc.perform(post("/restore/confirm").param("mode", "replace").param("confirmReplace", "true")
                        .session(session).with(user(signedIn)).with(csrf()))
                .andExpect(content().string(containsString("Deleted your previous 5 expenses and 2 budgets.")));

        assertThat(expenses()).extracting(Snapshot::title).containsExactly("Coffee");
        assertThat(budgets()).isEmpty();
        assertThat(expenseRepository.countByOwnerId(bob.getId())).isEqualTo(1);
    }

    @Test
    void badRowsAreSkippedAndReported() throws Exception {
        MockHttpSession session = previewOf("expenses.csv", "title,amount,date\n"
                + "Good one,10.00," + DAY + "\n"
                + "No amount,," + DAY + "\n"
                + "Future,10.00,2999-01-01\n");

        mockMvc.perform(post("/restore/confirm").session(session).with(user(signedIn)).with(csrf()))
                .andExpect(content().string(containsString("Added 1 expenses.")))
                .andExpect(content().string(containsString("expenses.csv row 3: Amount is required")))
                .andExpect(content().string(containsString("expenses.csv row 4: Date cannot be in the future")));

        assertThat(expenses()).extracting(Snapshot::title).contains("Good one").doesNotContain("No amount", "Future");
    }

    @Test
    void confirmingWithoutAPreviewAsksForTheFileAgain() throws Exception {
        mockMvc.perform(post("/restore/confirm").with(user(signedIn)).with(csrf()))
                .andExpect(content().string(containsString("Upload the file again")));
    }

    private MockHttpSession previewOf(String name, String csv) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(multipart("/restore").file(new MockMultipartFile("file", name, "text/csv", csv.getBytes()))
                        .session(session).with(user(signedIn)).with(csrf()))
                .andExpect(status().isOk());
        return session;
    }

    private void save(String title, String amount, Category category, LocalDate date, String note,
                      PaymentMethod method, Instant deletedAt) {
        Expense expense = new Expense(alice, title, new BigDecimal(amount), category, date, note);
        expense.setPaymentMethod(method);
        if (deletedAt != null) {
            expense.moveToTrash(deletedAt);
        }
        expenseRepository.save(expense);
    }

    private List<Snapshot> expenses() {
        return expenseRepository.findAllByOwnerIdOrderByDateAscIdAsc(alice.getId()).stream()
                .map(e -> new Snapshot(e.getTitle(), e.getAmount().setScale(2), e.getCategory(), e.getDate(),
                        e.getNote(), e.getPaymentMethod(), e.getDeletedAt()))
                .toList();
    }

    private List<String> budgets() {
        return budgetRepository.findAllByOwnerIdOrderByCategoryAsc(alice.getId()).stream()
                .map(b -> b.getCategory() + "=" + b.getMonthlyLimit().setScale(2))
                .toList();
    }
}
