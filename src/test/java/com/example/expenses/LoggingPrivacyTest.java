package com.example.expenses;

import com.example.expenses.config.TimeConfig;
import com.example.expenses.model.SavingsGoal;
import com.example.expenses.repository.SavingsGoalRepository;
import com.example.expenses.repository.UserRepository;
import com.example.expenses.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Goes through a typical session and checks the log never contains amounts, notes or passwords. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LoggingPrivacyTest {

    private static final String PASSWORD = "Sup3r-secret-pass";
    private static final String WRONG_PASSWORD = "wrong-secret-77";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavingsGoalRepository goalRepository;

    @Test
    void amountsNotesAndPasswordsNeverReachTheLog(CapturedOutput output) throws Exception {
        String today = LocalDate.now(TimeConfig.ZONE).toString();

        mockMvc.perform(post("/register").with(csrf())
                .param("username", "privacy")
                .param("password", PASSWORD)
                .param("confirmPassword", PASSWORD));
        mockMvc.perform(formLogin("/login").user("privacy").password(WRONG_PASSWORD));
        mockMvc.perform(formLogin("/login").user("privacy").password(PASSWORD));
        Long id = userRepository.findByUsername("privacy").orElseThrow().getId();
        AppUserDetails me = new AppUserDetails(id, "privacy", "unused", false);

        mockMvc.perform(post("/expenses").with(user(me)).with(csrf())
                .param("title", "Private dinner")
                .param("amount", "1234.56")
                .param("category", "FOOD")
                .param("date", today)
                .param("note", "my-private-note"));
        mockMvc.perform(post("/expenses").with(user(me)).with(csrf())   // invalid: rejected, still not logged
                .param("title", "Bad")
                .param("amount", "-98765.43")
                .param("category", "FOOD")
                .param("date", today)
                .param("note", "rejected-private-note"));
        mockMvc.perform(multipart("/expenses/import").file(new MockMultipartFile("file", "bank.csv", "text/csv",
                ("title,amount,date,note\nImported,777.77," + today + ",imported-private-note\n").getBytes()))
                .with(user(me)).with(csrf()));
        mockMvc.perform(multipart("/restore").file(new MockMultipartFile("file", "x.csv", "text/csv",
                ("title,amount,date,note\nRestored,555.55," + today + ",restored-private-note\n").getBytes()))
                .with(user(me)).with(csrf()));
        SavingsGoal goal = goalRepository.save(new SavingsGoal(userRepository.getReferenceById(id), "Trip",
                new BigDecimal("9000.00"), new BigDecimal("100.00"), null));
        mockMvc.perform(post("/goals/{id}/deposit", goal.getId()).param("amount", "4321.09").with(user(me)).with(csrf()));
        mockMvc.perform(post("/goals/{id}/withdraw", goal.getId()).param("amount", "99999.99").with(user(me)).with(csrf()));

        assertThat(output.getAll()).doesNotContain(
                PASSWORD, WRONG_PASSWORD,
                "1234.56", "98765.43", "777.77", "555.55", "4321.09", "99999.99", "9000.00",
                "my-private-note", "rejected-private-note", "imported-private-note", "restored-private-note");
    }
}
