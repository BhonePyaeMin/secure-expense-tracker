package com.example.expenses;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.security.AppUserDetails;
import com.example.expenses.security.SecurityConfig;
import com.example.expenses.service.CategorySuggester;
import com.example.expenses.service.CsvExportService;
import com.example.expenses.service.ExpenseNotFoundException;
import com.example.expenses.service.ExpenseService;
import com.example.expenses.service.QuickEntryParser;
import com.example.expenses.web.ExpenseController;
import com.example.expenses.web.MoneyFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ExpenseController.class)
@Import({SecurityConfig.class, CsvExportService.class, CategorySuggester.class, MoneyFormatter.class,
        QuickEntryParser.class})
class ExpenseControllerTest {

    private static final AppUserDetails ALICE = new AppUserDetails(1L, "alice", "unused", false);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @Test
    void listShowsExpensesAndKeepsFilterInPaginationLinks() throws Exception {
        Expense lunch = new Expense(null, "Lunch", new BigDecimal("1234.5"), Category.FOOD, LocalDate.of(2026, 10, 1), null);
        when(expenseService.search(eq(1L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(lunch), PageRequest.of(0, 10), 25));

        mockMvc.perform(get("/expenses").param("month", "2026-10").param("category", "FOOD").with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lunch")))
                .andExpect(content().string(containsString("฿1,234.50")))
                .andExpect(content().string(containsString("25 expenses")))
                .andExpect(content().string(containsString("Page 1 of 3")))
                .andExpect(content().string(containsString("/expenses?month=2026-10&amp;category=FOOD&amp;page=1")))
                .andExpect(content().string(containsString("alice")));
    }

    @Test
    void invalidExpenseRedisplaysFormWithErrorsAndKeepsInput() throws Exception {
        mockMvc.perform(post("/expenses").with(user(ALICE)).with(csrf())
                        .param("title", "")
                        .param("amount", "12.345")
                        .param("category", "FOOD")
                        .param("date", LocalDate.now().plusDays(1).toString())
                        .param("note", "keep me"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/form"))
                .andExpect(model().attributeHasFieldErrors("expenseForm", "title", "amount", "date"))
                .andExpect(content().string(containsString("Title is required")))
                .andExpect(content().string(containsString("at most 2 decimal places")))
                .andExpect(content().string(containsString("cannot be in the future")))
                .andExpect(content().string(containsString("keep me")));

        verifyNoInteractions(expenseService);
    }

    @Test
    void zeroAmountIsRejected() throws Exception {
        mockMvc.perform(post("/expenses").with(user(ALICE)).with(csrf())
                        .param("title", "Lunch")
                        .param("amount", "0")
                        .param("category", "FOOD")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("expenseForm", "amount", "DecimalMin"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void validExpenseIsSavedForTheSignedInUserAndRedirects() throws Exception {
        mockMvc.perform(post("/expenses").with(user(ALICE)).with(csrf())
                        .param("title", "Lunch")
                        .param("amount", "12.50")
                        .param("category", "FOOD")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses"));

        verify(expenseService).create(eq(1L), any());
    }

    @Test
    void missingExpenseShowsFriendlyNotFoundPage() throws Exception {
        when(expenseService.formFor(1L, 42L)).thenThrow(new ExpenseNotFoundException(42L));

        mockMvc.perform(get("/expenses/42/edit").with(user(ALICE)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("expenses/not-found"))
                .andExpect(content().string(containsString("Expense not found")));
    }

    @Test
    void invalidMonthParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/expenses").param("month", "banana").with(user(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("not a valid value for month")));
    }

    @Test
    void exportReturnsCsvDownloadForTheFilter() throws Exception {
        Expense lunch = new Expense(null, "Lunch, with \"Bob\"", new BigDecimal("85.00"), Category.FOOD, LocalDate.of(2026, 10, 1), null);
        when(expenseService.findAll(1L, new ExpenseFilter(YearMonth.of(2026, 10), null)))
                .thenReturn(List.of(lunch));

        mockMvc.perform(get("/expenses/export").param("month", "2026-10").with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"expenses-2026-10.csv\""))
                .andExpect(content().string("\uFEFFid,title,amount,category,date,note,payment_method\r\n"
                        + "null,\"Lunch, with \"\"Bob\"\"\",85.00,FOOD,2026-10-01,,\r\n"));
    }

    @Test
    void searchTermIsKeptInPaginationLinks() throws Exception {
        Expense tea = new Expense(null, "Milk tea", new BigDecimal("45.00"), Category.FOOD, LocalDate.of(2026, 10, 1), null);
        when(expenseService.search(eq(1L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(tea), PageRequest.of(0, 10), 25));

        mockMvc.perform(get("/expenses").param("q", "milk tea & cake").with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("q=milk%20tea%20%26%20cake&amp;page=1")));
    }

    @Test
    void suggestsCategoryForATitle() throws Exception {
        mockMvc.perform(get("/expenses/suggest-category").param("title", "grab ride").with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("TRANSPORT"))
                .andExpect(jsonPath("$.label").value("Transport"));
    }

    @Test
    void yourOwnHistoryBeatsTheKeywordRules() throws Exception {
        when(expenseService.lastCategoryFor(1L, "grab ride")).thenReturn(java.util.Optional.of(Category.STUDY));

        mockMvc.perform(get("/expenses/suggest-category").param("title", "grab ride").with(user(ALICE)))
                .andExpect(jsonPath("$.category").value("STUDY"))
                .andExpect(jsonPath("$.source").value("history"));
    }

    @Test
    void quickAddUsesTheCategoryFromLastTime() throws Exception {
        when(expenseService.lastCategoryFor(1L, "Lunch")).thenReturn(java.util.Optional.of(Category.OTHER));

        mockMvc.perform(get("/expenses/new").param("quick", "lunch 85").with(user(ALICE)))
                .andExpect(model().attribute("expenseForm", org.hamcrest.Matchers.hasProperty("category",
                        org.hamcrest.Matchers.equalTo(Category.OTHER))));
    }

    @Test
    void repeatAddsACopyForToday() throws Exception {
        Expense copy = new Expense(null, "Coffee", new BigDecimal("55.00"), Category.FOOD, LocalDate.now(), null);
        when(expenseService.repeat(eq(1L), eq(7L), any())).thenReturn(copy);

        mockMvc.perform(post("/expenses/7/repeat").with(user(ALICE)).with(csrf()))
                .andExpect(redirectedUrl("/expenses"));

        verify(expenseService).repeat(1L, 7L, LocalDate.now());
    }

    @Test
    void noSuggestionGivesNoContent() throws Exception {
        mockMvc.perform(get("/expenses/suggest-category").param("title", "something else").with(user(ALICE)))
                .andExpect(status().isNoContent());
    }

    @Test
    void quickAddPrefillsTheFormWithoutSaving() throws Exception {
        mockMvc.perform(get("/expenses/new").param("quick", "lunch 85 baht yesterday").with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("expenseForm", org.hamcrest.Matchers.hasProperty("title",
                        org.hamcrest.Matchers.equalTo("Lunch"))))
                .andExpect(content().string(containsString("value=\"" + LocalDate.now().minusDays(1) + "\"")))
                .andExpect(content().string(containsString("Check the details, then save.")));

        verify(expenseService, org.mockito.Mockito.never()).create(any(), any());
    }

    @Test
    void newExpensesDefaultToCashAndThePaymentMethodIsSaved() throws Exception {
        mockMvc.perform(get("/expenses/new").with(user(ALICE)))
                .andExpect(content().string(containsString("<option value=\"CASH\" selected=\"selected\">Cash</option>")));

        mockMvc.perform(post("/expenses").with(user(ALICE)).with(csrf())
                        .param("title", "Bubble tea")
                        .param("amount", "60")
                        .param("category", "FOOD")
                        .param("date", LocalDate.now().toString())
                        .param("paymentMethod", "PROMPTPAY"))
                .andExpect(redirectedUrl("/expenses"));

        verify(expenseService).create(eq(1L), org.mockito.ArgumentMatchers.argThat(form ->
                form.getPaymentMethod() == com.example.expenses.model.PaymentMethod.PROMPTPAY));
    }

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mockMvc.perform(get("/expenses"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }
}
