package com.example.expenses;

import com.example.expenses.config.TimeConfig;
import com.example.expenses.model.AuditAction;
import com.example.expenses.model.AuditEntry;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.User;
import com.example.expenses.repository.AuditEntryRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.UserRepository;
import com.example.expenses.security.AppUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Soft delete: delete moves to the trash, which can be restored or emptied for good. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrashTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    private AppUserDetails alice;
    private AppUserDetails bob;
    private Expense concert;

    @BeforeEach
    void setUp() {
        User aliceUser = userRepository.save(new User("alice", "hash"));
        User bobUser = userRepository.save(new User("bob", "hash"));
        alice = new AppUserDetails(aliceUser.getId(), "alice", "unused", false);
        bob = new AppUserDetails(bobUser.getId(), "bob", "unused", false);
        concert = expenseRepository.save(new Expense(aliceUser, "Concert ticket", new BigDecimal("1200.00"),
                Category.FUN, LocalDate.now(TimeConfig.ZONE), null));
    }

    @Test
    void deleteMovesToTrashAndHidesItEverywhere() throws Exception {
        mockMvc.perform(post("/expenses/{id}/delete", concert.getId()).with(user(alice)).with(csrf()))
                .andExpect(redirectedUrl("/expenses"))
                .andExpect(flash().attribute("undoId", concert.getId()));

        assertThat(expenseRepository.findById(concert.getId())).get()
                .satisfies(e -> assertThat(e.isDeleted()).isTrue());
        mockMvc.perform(get("/expenses").with(user(alice)))
                .andExpect(content().string(not(containsString("Concert ticket"))))
                .andExpect(content().string(containsString("Trash (1)")));
        mockMvc.perform(get("/summary").with(user(alice)))
                .andExpect(content().string(not(containsString("฿1,200.00"))));
        mockMvc.perform(get("/expenses/export").with(user(alice)))
                .andExpect(content().string(not(containsString("Concert ticket"))));
        mockMvc.perform(get("/trash").with(user(alice)))
                .andExpect(content().string(containsString("Concert ticket")));
    }

    @Test
    void restoreFromTrashBringsItBackIntoListsAndTotals() throws Exception {
        mockMvc.perform(post("/expenses/{id}/delete", concert.getId()).with(user(alice)).with(csrf()));

        mockMvc.perform(post("/trash/{id}/restore", concert.getId()).with(user(alice)).with(csrf()))
                .andExpect(redirectedUrl("/trash"));

        assertThat(expenseRepository.findById(concert.getId())).get()
                .satisfies(e -> assertThat(e.isDeleted()).isFalse());
        mockMvc.perform(get("/expenses").with(user(alice)))
                .andExpect(content().string(containsString("Concert ticket")));
        mockMvc.perform(get("/summary").with(user(alice)))
                .andExpect(content().string(containsString("฿1,200.00")));
        mockMvc.perform(get("/trash").with(user(alice)))
                .andExpect(content().string(containsString("The trash is empty.")));
        assertThat(auditActions()).contains(AuditAction.EXPENSE_DELETED, AuditAction.EXPENSE_RESTORED);
    }

    @Test
    void undoOnTheListRestoresAndReturnsToTheList() throws Exception {
        mockMvc.perform(post("/expenses/{id}/delete", concert.getId()).with(user(alice)).with(csrf()));

        mockMvc.perform(post("/trash/{id}/restore", concert.getId()).param("from", "list")
                        .with(user(alice)).with(csrf()))
                .andExpect(redirectedUrl("/expenses"));

        assertThat(expenseRepository.findById(concert.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void deleteForeverRemovesTheRowButOnlyFromTheTrash() throws Exception {
        mockMvc.perform(post("/trash/{id}/delete", concert.getId()).with(user(alice)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(expenseRepository.findById(concert.getId())).isPresent();

        mockMvc.perform(post("/expenses/{id}/delete", concert.getId()).with(user(alice)).with(csrf()));
        mockMvc.perform(post("/trash/{id}/delete", concert.getId()).with(user(alice)).with(csrf()))
                .andExpect(redirectedUrl("/trash"));

        assertThat(expenseRepository.findById(concert.getId())).isEmpty();
        assertThat(auditActions()).contains(AuditAction.EXPENSE_PURGED);
    }

    @Test
    void trashedExpensesCannotBeEditedOrRepeated() throws Exception {
        mockMvc.perform(post("/expenses/{id}/delete", concert.getId()).with(user(alice)).with(csrf()));

        mockMvc.perform(get("/expenses/{id}/edit", concert.getId()).with(user(alice)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/expenses/{id}/repeat", concert.getId()).with(user(alice)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUsersCannotSeeRestoreOrEmptyYourTrash() throws Exception {
        mockMvc.perform(post("/expenses/{id}/delete", concert.getId()).with(user(alice)).with(csrf()));

        mockMvc.perform(get("/trash").with(user(bob)))
                .andExpect(content().string(not(containsString("Concert ticket"))));
        mockMvc.perform(post("/trash/{id}/restore", concert.getId()).with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/trash/{id}/delete", concert.getId()).with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(expenseRepository.findById(concert.getId()).orElseThrow().isDeleted()).isTrue();
    }

    private java.util.List<AuditAction> auditActions() {
        return auditEntryRepository.findByUserIdOrderByCreatedAtDescIdDesc(alice.getId(), Pageable.unpaged())
                .map(AuditEntry::getAction).getContent();
    }
}
