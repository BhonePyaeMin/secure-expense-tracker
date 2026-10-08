package com.example.expenses;

import com.example.expenses.model.AuditAction;
import com.example.expenses.model.AuditEntry;
import com.example.expenses.model.SavingsGoal;
import com.example.expenses.model.User;
import com.example.expenses.repository.AuditEntryRepository;
import com.example.expenses.repository.SavingsGoalRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoalsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavingsGoalRepository goalRepository;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    private AppUserDetails alice;
    private AppUserDetails bob;

    @BeforeEach
    void setUp() {
        User aliceUser = userRepository.save(new User("alice", "hash"));
        User bobUser = userRepository.save(new User("bob", "hash"));
        alice = new AppUserDetails(aliceUser.getId(), "alice", "unused", false);
        bob = new AppUserDetails(bobUser.getId(), "bob", "unused", false);
    }

    @Test
    void createAGoalAndSaveTowardIt() throws Exception {
        mockMvc.perform(post("/goals").with(user(alice)).with(csrf())
                        .param("name", "New laptop")
                        .param("targetAmount", "35000")
                        .param("alreadySaved", "12000"))
                .andExpect(redirectedUrl("/goals"));
        SavingsGoal goal = onlyGoal();

        mockMvc.perform(post("/goals/{id}/deposit", goal.getId()).param("amount", "500")
                        .with(user(alice)).with(csrf()))
                .andExpect(redirectedUrl("/goals"));

        assertThat(goal.getSavedAmount()).isEqualByComparingTo("12500");
        mockMvc.perform(get("/goals").with(user(alice)))
                .andExpect(content().string(containsString("New laptop")))
                .andExpect(content().string(containsString("฿12,500.00")))
                .andExpect(content().string(containsString("35.7%")));
        assertThat(auditActions()).contains(AuditAction.GOAL_CREATED, AuditAction.GOAL_DEPOSIT);
    }

    @Test
    void takingOutMoreThanIsSavedIsRefused() throws Exception {
        SavingsGoal goal = goalRepository.save(new SavingsGoal(userRepository.getReferenceById(alice.getId()),
                "Trip", new BigDecimal("5000"), new BigDecimal("300"), null));

        mockMvc.perform(post("/goals/{id}/withdraw", goal.getId()).param("amount", "301")
                        .with(user(alice)).with(csrf()))
                .andExpect(flash().attribute("error", "You can't take out more than is saved."));

        assertThat(goal.getSavedAmount()).isEqualByComparingTo("300");
    }

    @Test
    void invalidAmountsAndGoalsAreRejected() throws Exception {
        SavingsGoal goal = goalRepository.save(new SavingsGoal(userRepository.getReferenceById(alice.getId()),
                "Trip", new BigDecimal("5000"), BigDecimal.ZERO, null));

        mockMvc.perform(post("/goals/{id}/deposit", goal.getId()).param("amount", "-5")
                        .with(user(alice)).with(csrf()))
                .andExpect(flash().attribute("error", "Amount must be greater than 0"));
        mockMvc.perform(post("/goals").with(user(alice)).with(csrf())
                        .param("name", "")
                        .param("targetAmount", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Name is required")))
                .andExpect(content().string(containsString("Target must be greater than 0")));
    }

    @Test
    void otherUsersCannotSeeOrChangeYourGoals() throws Exception {
        SavingsGoal goal = goalRepository.save(new SavingsGoal(userRepository.getReferenceById(alice.getId()),
                "Alice secret fund", new BigDecimal("5000"), new BigDecimal("300"), null));

        mockMvc.perform(get("/goals").with(user(bob)))
                .andExpect(content().string(not(containsString("Alice secret fund"))));
        mockMvc.perform(post("/goals/{id}/deposit", goal.getId()).param("amount", "1").with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/goals/{id}/withdraw", goal.getId()).param("amount", "1").with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/goals/{id}/delete", goal.getId()).with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(goal.getSavedAmount()).isEqualByComparingTo("300");
        assertThat(goalRepository.findById(goal.getId())).isPresent();
    }

    private SavingsGoal onlyGoal() {
        assertThat(goalRepository.findAll()).hasSize(1);
        return goalRepository.findAll().get(0);
    }

    private java.util.List<AuditAction> auditActions() {
        return auditEntryRepository.findByUserIdOrderByCreatedAtDescIdDesc(alice.getId(), Pageable.unpaged())
                .map(AuditEntry::getAction).getContent();
    }
}
