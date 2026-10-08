package com.example.expenses;

import com.example.expenses.config.TimeConfig;
import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Today" comes from the injected Clock in Bangkok time. These tests move a test clock to the
 * edges: midnight, the end of a month, and 29 February in a leap year.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TimeRulesTest {

    /** A clock the test can move. Replaces the system clock for this test class. */
    static class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-08T05:00:00Z");

        void set(String instant) {
            now = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return TimeConfig.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class TestClock {

        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovableClock clock;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    private User alice;
    private AppUserDetails signedIn;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("alice", "hash"));
        signedIn = new AppUserDetails(alice.getId(), "alice", "unused", false);
    }

    @Test
    void todayChangesAtMidnightBangkokTimeNotUtc() throws Exception {
        clock.set("2026-10-08T16:59:59Z"); // 23:59:59 on 8 October in Bangkok

        mockMvc.perform(addExpenseDated("2026-10-09"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Date cannot be in the future")));
        mockMvc.perform(get("/expenses/new").with(user(signedIn)))
                .andExpect(content().string(containsString("value=\"2026-10-08\"")));

        clock.set("2026-10-08T17:00:00Z"); // 00:00 on 9 October in Bangkok, still 8 October in UTC

        mockMvc.perform(addExpenseDated("2026-10-09"))
                .andExpect(redirectedUrl("/expenses"));
        mockMvc.perform(get("/expenses/new").with(user(signedIn)))
                .andExpect(content().string(containsString("value=\"2026-10-09\"")));
    }

    @Test
    void lastSecondOfTheMonthAndFirstSecondOfTheNext() throws Exception {
        budgetRepository.save(new Budget(alice, Category.FOOD, new BigDecimal("3100.00")));

        clock.set("2026-10-31T16:59:59Z"); // 23:59:59 on 31 October in Bangkok
        mockMvc.perform(get("/summary").with(user(signedIn)))
                .andExpect(content().string(containsString("Summary for October 2026")))
                .andExpect(content().string(containsString("of your budgets left for 1 day")))
                .andExpect(content().string(containsString("Projected by 31 Oct")));

        clock.set("2026-10-31T17:00:00Z"); // 00:00 on 1 November in Bangkok
        mockMvc.perform(get("/summary").with(user(signedIn)))
                .andExpect(content().string(containsString("Summary for November 2026")))
                .andExpect(content().string(containsString("of your budgets left for 30 days")));
    }

    @Test
    void twentyNinthOfFebruaryInALeapYear() throws Exception {
        clock.set("2028-02-29T05:00:00Z"); // midday on 29 February 2028 in Bangkok
        budgetRepository.save(new Budget(alice, Category.RENT, new BigDecimal("5000.00")));

        mockMvc.perform(addExpenseDated("2028-02-29")).andExpect(redirectedUrl("/expenses"));

        // A recurring expense due on the 31st falls on the 29th in a leap-year February
        mockMvc.perform(post("/recurring").with(user(signedIn)).with(csrf())
                        .param("title", "Rent")
                        .param("amount", "4500.00")
                        .param("category", "RENT")
                        .param("firstDate", "2028-01-31"))
                .andExpect(redirectedUrl("/recurring"));
        assertThat(expenseRepository.findAll()).filteredOn(e -> e.getTitle().equals("Rent"))
                .extracting(Expense::getDate)
                .containsExactlyInAnyOrder(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29));

        mockMvc.perform(get("/summary").with(user(signedIn)))
                .andExpect(content().string(containsString("Summary for February 2028")))
                .andExpect(content().string(containsString("over 29 days")))
                .andExpect(content().string(containsString("of your budgets left for 1 day")));
    }

    private MockHttpServletRequestBuilder addExpenseDated(String date) {
        return post("/expenses").with(user(signedIn)).with(csrf())
                .param("title", "Late snack")
                .param("amount", "40.00")
                .param("category", "FOOD")
                .param("date", date);
    }
}
