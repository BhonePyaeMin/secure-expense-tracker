package com.example.expenses;

import com.example.expenses.dto.CategorySummary;
import com.example.expenses.dto.DailySpending;
import com.example.expenses.dto.DailyTotal;
import com.example.expenses.dto.MonthlySummary;
import com.example.expenses.model.Category;
import com.example.expenses.security.AppUserDetails;
import com.example.expenses.security.SecurityConfig;
import com.example.expenses.service.BudgetService;
import com.example.expenses.service.SummaryService;
import com.example.expenses.web.MoneyFormatter;
import com.example.expenses.web.SummaryController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SummaryController.class)
@Import({SecurityConfig.class, MoneyFormatter.class})
class SummaryControllerTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final AppUserDetails ALICE = new AppUserDetails(1L, "alice", "unused", false);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SummaryService summaryService;

    @MockitoBean
    private BudgetService budgetService;

    @BeforeEach
    void noDailySpendingByDefault() {
        when(summaryService.dailySpending(anyLong(), any())).thenReturn(new DailySpending(OCTOBER, List.of()));
    }

    @Test
    void summaryPageShowsTheDailyChartWithATableView() throws Exception {
        when(summaryService.summarize(1L, OCTOBER)).thenReturn(new MonthlySummary(OCTOBER, new BigDecimal("65.00"), List.of()));
        when(summaryService.dailySpending(1L, OCTOBER)).thenReturn(new DailySpending(OCTOBER, List.of(
                new DailyTotal(LocalDate.of(2026, 10, 1), new BigDecimal("0.00")),
                new DailyTotal(LocalDate.of(2026, 10, 2), new BigDecimal("65.00")))));

        mockMvc.perform(get("/summary").param("month", "2026-10").with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"daily-chart\"")))
                .andExpect(content().string(containsString("data-amounts=\"0.00,65.00\"")))
                .andExpect(content().string(containsString("Show as a table")))
                .andExpect(content().string(containsString("integrity=\"sha512-")));
    }

    @Test
    void summaryPageHighlightsOverBudgetRows() throws Exception {
        when(summaryService.summarize(1L, OCTOBER)).thenReturn(new MonthlySummary(OCTOBER, new BigDecimal("3100.00"), List.of(
                new CategorySummary(Category.FOOD, new BigDecimal("3100.00"), new BigDecimal("3000.00"),
                        new BigDecimal("-100.00"), true, new BigDecimal("100.0")))));

        mockMvc.perform(get("/summary").param("month", "2026-10").with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3,100.00")))
                .andExpect(content().string(containsString("-฿100.00")))
                .andExpect(content().string(containsString("class=\"over\"")))
                .andExpect(content().string(containsString("width: 100.0%")))
                .andExpect(content().string(containsString("/summary?month=2026-09")));
    }

    @Test
    void savingABudgetRedirectsBackToTheSameMonth() throws Exception {
        mockMvc.perform(post("/budgets").with(user(ALICE)).with(csrf())
                        .param("category", "FOOD")
                        .param("monthlyLimit", "3000")
                        .param("month", "2026-10"))
                .andExpect(redirectedUrl("/summary?month=2026-10"));

        verify(budgetService).setLimit(1L, Category.FOOD, new BigDecimal("3000"));
    }

    @Test
    void invalidBudgetShowsErrors() throws Exception {
        when(summaryService.summarize(anyLong(), any())).thenReturn(new MonthlySummary(OCTOBER, BigDecimal.ZERO, List.of()));

        mockMvc.perform(post("/budgets").with(user(ALICE)).with(csrf())
                        .param("category", "FOOD")
                        .param("monthlyLimit", "-5")
                        .param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("budgetForm", "monthlyLimit"))
                .andExpect(content().string(containsString("Limit must be greater than 0")));

        verify(budgetService, never()).setLimit(anyLong(), any(), any());
    }
}
