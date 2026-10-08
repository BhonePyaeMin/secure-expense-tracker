package com.example.expenses;

import com.example.expenses.model.AuditAction;
import com.example.expenses.model.AuditEntry;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.User;
import com.example.expenses.repository.AuditEntryRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.IncomeRepository;
import com.example.expenses.repository.UserRepository;
import com.example.expenses.security.AppUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks with the real security setup and database: per-user data isolation,
 * CSRF, login, lockout, registration and the audit log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User alice;
    private Expense bobsExpense;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("alice", passwordEncoder.encode("alice-password")));
        User bob = userRepository.save(new User("bob", passwordEncoder.encode("bob-password")));
        expenseRepository.save(new Expense(alice, "Alice groceries", new BigDecimal("300.00"), Category.FOOD, LocalDate.now(), null));
        bobsExpense = expenseRepository.save(
                new Expense(bob, "Bob secret dinner", new BigDecimal("85.00"), Category.FOOD, LocalDate.now(), "private note"));
    }

    private AppUserDetails signedInAlice() {
        return new AppUserDetails(alice.getId(), "alice", "unused", false);
    }

    // --- Access control: user A cannot read, edit or delete user B's expenses ---

    @Test
    void listShowsOnlyYourOwnExpenses() throws Exception {
        mockMvc.perform(get("/expenses").with(user(signedInAlice())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice groceries")))
                .andExpect(content().string(not(containsString("Bob secret dinner"))));
    }

    @Test
    void cannotOpenAnotherUsersExpense() throws Exception {
        mockMvc.perform(get("/expenses/{id}/edit", bobsExpense.getId()).with(user(signedInAlice())))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Bob secret dinner"))));
    }

    @Test
    void cannotEditAnotherUsersExpense() throws Exception {
        mockMvc.perform(post("/expenses/{id}", bobsExpense.getId()).with(user(signedInAlice())).with(csrf())
                        .param("title", "Hacked")
                        .param("amount", "1.00")
                        .param("category", "FUN")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isNotFound());

        assertThat(expenseRepository.findById(bobsExpense.getId()))
                .get().extracting(Expense::getTitle).isEqualTo("Bob secret dinner");
    }

    @Test
    void cannotDeleteAnotherUsersExpense() throws Exception {
        mockMvc.perform(post("/expenses/{id}/delete", bobsExpense.getId()).with(user(signedInAlice())).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(expenseRepository.findById(bobsExpense.getId())).isPresent();
    }

    @Test
    void exportContainsOnlyYourOwnExpenses() throws Exception {
        mockMvc.perform(get("/expenses/export").with(user(signedInAlice())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice groceries")))
                .andExpect(content().string(not(containsString("Bob secret dinner"))));
    }

    @Test
    void summaryCountsOnlyYourOwnExpenses() throws Exception {
        mockMvc.perform(get("/summary").with(user(signedInAlice())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("300.00")))
                .andExpect(content().string(not(containsString("385.00"))));
    }

    // --- Authentication ---

    @Test
    void anonymousUsersAreRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/summary"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void postsWithoutCsrfTokenAreRejected() throws Exception {
        mockMvc.perform(post("/expenses/{id}/delete", bobsExpense.getId()).with(user(signedInAlice())))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPasswordSignsIn() throws Exception {
        mockMvc.perform(formLogin("/login").user("alice").password("alice-password"))
                .andExpect(authenticated().withUsername("alice"))
                .andExpect(redirectedUrl("/expenses"));
    }

    @Test
    void usernamesAreCaseInsensitive() throws Exception {
        mockMvc.perform(formLogin("/login").user("ALICE").password("alice-password"))
                .andExpect(authenticated().withUsername("alice"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(formLogin("/login").user("alice").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(formLogin("/login").user("alice").password("wrong"))
                    .andExpect(redirectedUrl("/login?error"));
        }

        mockMvc.perform(formLogin("/login").user("alice").password("alice-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?locked"));

        assertThat(auditActionsFor(alice)).contains(AuditAction.ACCOUNT_LOCKED);
    }

    @Test
    void registeringStoresABcryptHashNotThePassword() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "Carol")
                        .param("password", "carol-password")
                        .param("confirmPassword", "carol-password"))
                .andExpect(redirectedUrl("/login"));

        User carol = userRepository.findByUsername("carol").orElseThrow();
        assertThat(carol.getPasswordHash()).startsWith("$2").doesNotContain("carol-password");
        assertThat(passwordEncoder.matches("carol-password", carol.getPasswordHash())).isTrue();
    }

    @Test
    void registeringATakenUsernameShowsAnError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "Alice")
                        .param("password", "another-password")
                        .param("confirmPassword", "another-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already taken")));
    }

    @Test
    void registeringWithMismatchedPasswordsShowsAnError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "dave")
                        .param("password", "dave-password")
                        .param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Passwords don&#39;t match")));
    }

    // --- Recurring expenses page (full stack) ---

    @Test
    void recurringExpenseCanBeCreatedFromThePage() throws Exception {
        mockMvc.perform(post("/recurring").with(user(signedInAlice())).with(csrf())
                        .param("title", "Phone plan")
                        .param("amount", "399.00")
                        .param("category", "OTHER")
                        .param("firstDate", LocalDate.now().toString()))
                .andExpect(redirectedUrl("/recurring"));

        mockMvc.perform(get("/recurring").with(user(signedInAlice())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phone plan")))
                .andExpect(content().string(containsString("฿399.00")));
        mockMvc.perform(get("/expenses").with(user(signedInAlice())))
                .andExpect(content().string(containsString("Phone plan")));
    }

    @Test
    void recurringFirstDateMoreThanAYearAgoIsRejected() throws Exception {
        mockMvc.perform(post("/recurring").with(user(signedInAlice())).with(csrf())
                        .param("title", "Old thing")
                        .param("amount", "10.00")
                        .param("category", "OTHER")
                        .param("firstDate", LocalDate.now().minusYears(2).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("at most one year ago")));
    }

    // --- CSV import (full stack) ---

    @Test
    void importedExpensesBelongToTheSignedInUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bank.csv", "text/csv",
                "date,description,amount\n2026-10-01,Grab ride,120\n2026-10-02,Iced tea,25\n".getBytes());

        mockMvc.perform(multipart("/expenses/import").file(file).with(user(signedInAlice())).with(csrf()))
                .andExpect(redirectedUrl("/expenses"));

        assertThat(expenseRepository.findAll()).filteredOn(e -> e.getOwner().getId().equals(alice.getId()))
                .extracting(Expense::getTitle).contains("Grab ride", "Iced tea");
        assertThat(auditActionsFor(alice)).contains(AuditAction.EXPENSES_IMPORTED);
    }

    @Test
    void invalidImportSavesNothingAndListsTheProblems() throws Exception {
        long before = expenseRepository.count();
        MockMultipartFile file = new MockMultipartFile("file", "bad.csv", "text/csv",
                "title,amount,date\nLunch,85,2026-10-01\nBroken,-1,2026-10-02\n".getBytes());

        mockMvc.perform(multipart("/expenses/import").file(file).with(user(signedInAlice())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Row 3: Amount must be greater than 0")));

        assertThat(expenseRepository.count()).isEqualTo(before);
    }

    // --- Repeat ---

    @Test
    void repeatCopiesYourExpenseWithTodaysDateButNotSomeoneElses() throws Exception {
        Expense groceries = expenseRepository.save(new Expense(alice, "Weekly groceries", new BigDecimal("410.00"),
                Category.FOOD, LocalDate.now().minusDays(7), "Market"));

        mockMvc.perform(post("/expenses/{id}/repeat", groceries.getId()).with(user(signedInAlice())).with(csrf()))
                .andExpect(redirectedUrl("/expenses"));

        assertThat(expenseRepository.findAll()).filteredOn(e -> e.getTitle().equals("Weekly groceries"))
                .extracting(Expense::getDate)
                .containsExactlyInAnyOrder(LocalDate.now().minusDays(7), LocalDate.now());

        mockMvc.perform(post("/expenses/{id}/repeat", bobsExpense.getId()).with(user(signedInAlice())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // --- Insights ---

    @Test
    void insightsComparesYourOwnSpendingWithLastMonth() throws Exception {
        expenseRepository.save(new Expense(alice, "Last month groceries", new BigDecimal("200.00"), Category.FOOD,
                LocalDate.now().minusMonths(1).withDayOfMonth(1), null));

        mockMvc.perform(get("/insights").with(user(signedInAlice())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Compared with")))
                .andExpect(content().string(containsString("Food")))
                .andExpect(content().string(containsString("+50.0%")))
                .andExpect(content().string(containsString("Top 5 expenses")))
                .andExpect(content().string(containsString("Alice groceries")))
                .andExpect(content().string(not(containsString("Bob secret dinner"))));
    }

    // --- Income ---

    @Test
    void incomeShowsUpInTheBalanceAndIsPrivate() throws Exception {
        mockMvc.perform(post("/income").with(user(signedInAlice())).with(csrf())
                        .param("source", "Alice tutoring")
                        .param("amount", "1000.00")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection());

        // Alice spent 300.00 this month, so her balance is 700.00
        mockMvc.perform(get("/summary").with(user(signedInAlice())))
                .andExpect(content().string(containsString("฿1,000.00")))
                .andExpect(content().string(containsString("฿700.00")));

        Long incomeId = incomeRepository.findAll().get(0).getId();
        AppUserDetails bob = new AppUserDetails(userRepository.findByUsername("bob").orElseThrow().getId(),
                "bob", "unused", false);
        mockMvc.perform(get("/income").with(user(bob)))
                .andExpect(content().string(not(containsString("Alice tutoring"))));
        mockMvc.perform(post("/income/{id}/delete", incomeId).with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(incomeRepository.findById(incomeId)).isPresent();
    }

    // --- Audit log ---

    @Test
    void createEditAndDeleteAreAudited() throws Exception {
        mockMvc.perform(post("/expenses").with(user(signedInAlice())).with(csrf())
                .param("title", "Coffee")
                .param("amount", "55.00")
                .param("category", "FOOD")
                .param("date", LocalDate.now().toString()));
        Expense coffee = expenseRepository.findAll().stream()
                .filter(e -> e.getTitle().equals("Coffee")).findFirst().orElseThrow();

        mockMvc.perform(post("/expenses/{id}", coffee.getId()).with(user(signedInAlice())).with(csrf())
                .param("title", "Coffee")
                .param("amount", "60.00")
                .param("category", "FOOD")
                .param("date", LocalDate.now().toString()));
        mockMvc.perform(post("/expenses/{id}/delete", coffee.getId()).with(user(signedInAlice())).with(csrf()));

        assertThat(auditActionsFor(alice)).containsSequence(
                AuditAction.EXPENSE_DELETED, AuditAction.EXPENSE_UPDATED, AuditAction.EXPENSE_CREATED);
        assertThat(auditEntryRepository.findByUserIdOrderByCreatedAtDescIdDesc(alice.getId(), Pageable.unpaged())
                .getContent().get(1).getDetails()).contains("amount 55.00 → 60.00");

        mockMvc.perform(get("/activity").with(user(signedInAlice())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Expense edited")));
    }

    private java.util.List<AuditAction> auditActionsFor(User user) {
        return auditEntryRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), Pageable.unpaged())
                .map(AuditEntry::getAction).getContent();
    }
}
