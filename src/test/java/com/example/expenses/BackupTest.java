package com.example.expenses;

import com.example.expenses.model.AuditAction;
import com.example.expenses.model.AuditEntry;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.Income;
import com.example.expenses.model.PaymentMethod;
import com.example.expenses.model.User;
import com.example.expenses.repository.AuditEntryRepository;
import com.example.expenses.repository.BudgetRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackupTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    private AppUserDetails alice;

    @BeforeEach
    void setUp() {
        User aliceUser = userRepository.save(new User("alice", "secret-hash-of-alice"));
        User bob = userRepository.save(new User("bob", "secret-hash-of-bob"));
        alice = new AppUserDetails(aliceUser.getId(), "alice", "unused", false);

        Expense lunch = new Expense(aliceUser, "Lunch, with friends", new BigDecimal("85.00"), Category.FOOD,
                LocalDate.of(2026, 10, 1), null);
        lunch.setPaymentMethod(PaymentMethod.PROMPTPAY);
        expenseRepository.save(lunch);
        Expense trashed = new Expense(aliceUser, "Old gadget", new BigDecimal("990.00"), Category.OTHER,
                LocalDate.of(2026, 10, 2), null);
        trashed.moveToTrash(Instant.parse("2026-10-03T10:00:00Z"));
        expenseRepository.save(trashed);
        incomeRepository.save(new Income(aliceUser, new BigDecimal("8000.00"), "Allowance", LocalDate.of(2026, 10, 1)));
        budgetRepository.save(new Budget(aliceUser, Category.FOOD, new BigDecimal("3000.00")));

        expenseRepository.save(new Expense(bob, "Bob private thing", new BigDecimal("1.00"), Category.FUN,
                LocalDate.of(2026, 10, 1), null));
        incomeRepository.save(new Income(bob, new BigDecimal("99.00"), "Bob salary", LocalDate.of(2026, 10, 1)));
    }

    @Test
    void backupIsAZipOfYourOwnDataOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/backup").with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"expense-tracker-alice-" + LocalDate.now() + ".zip\""))
                .andReturn();

        Map<String, String> files = unzip(result.getResponse().getContentAsByteArray());

        assertThat(files).containsOnlyKeys("expenses.csv", "income.csv", "budgets.csv", "recurring.csv");
        assertThat(files.get("expenses.csv"))
                .startsWith("\uFEFFid,title,amount,category,date,note,payment_method,deleted_at,recurring_expense_id")
                .contains("\"Lunch, with friends\",85.00,FOOD,2026-10-01,,PROMPTPAY,,")
                .contains("Old gadget,990.00,OTHER,2026-10-02,,,2026-10-03T10:00:00Z,")
                .doesNotContain("Bob");
        assertThat(files.get("income.csv")).contains("Allowance,8000.00,2026-10-01").doesNotContain("Bob");
        assertThat(files.get("budgets.csv")).contains("FOOD,3000.00");
        assertThat(String.join("", files.values())).doesNotContain("secret-hash");
    }

    @Test
    void downloadingABackupIsAudited() throws Exception {
        mockMvc.perform(get("/backup").with(user(alice))).andExpect(status().isOk());

        assertThat(auditEntryRepository.findByUserIdOrderByCreatedAtDescIdDesc(alice.getId(), Pageable.unpaged())
                .map(AuditEntry::getAction).getContent()).contains(AuditAction.BACKUP_DOWNLOADED);
    }

    @Test
    void backupNeedsSigningIn() throws Exception {
        mockMvc.perform(get("/backup")).andExpect(status().is3xxRedirection());
    }

    private static Map<String, String> unzip(byte[] zipBytes) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
