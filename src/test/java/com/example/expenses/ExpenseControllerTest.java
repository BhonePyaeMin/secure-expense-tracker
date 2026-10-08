package com.example.expenses;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.service.CsvExportService;
import com.example.expenses.service.ExpenseNotFoundException;
import com.example.expenses.service.ExpenseService;
import com.example.expenses.web.ExpenseController;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ExpenseController.class)
@Import(CsvExportService.class)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @Test
    void listShowsExpensesAndKeepsFilterInPaginationLinks() throws Exception {
        Expense lunch = new Expense("Lunch", new BigDecimal("1234.5"), Category.FOOD, LocalDate.of(2026, 10, 1), null);
        when(expenseService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(lunch), PageRequest.of(0, 10), 25));

        mockMvc.perform(get("/expenses").param("month", "2026-10").param("category", "FOOD"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lunch")))
                .andExpect(content().string(containsString("1,234.50")))
                .andExpect(content().string(containsString("25 expenses")))
                .andExpect(content().string(containsString("Page 1 of 3")))
                .andExpect(content().string(containsString("/expenses?month=2026-10&amp;category=FOOD&amp;page=1")));
    }

    @Test
    void invalidExpenseRedisplaysFormWithErrorsAndKeepsInput() throws Exception {
        mockMvc.perform(post("/expenses")
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
        mockMvc.perform(post("/expenses")
                        .param("title", "Lunch")
                        .param("amount", "0")
                        .param("category", "FOOD")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("expenseForm", "amount", "DecimalMin"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void validExpenseIsSavedAndRedirects() throws Exception {
        mockMvc.perform(post("/expenses")
                        .param("title", "Lunch")
                        .param("amount", "12.50")
                        .param("category", "FOOD")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses"));

        verify(expenseService).create(any());
    }

    @Test
    void missingExpenseShowsFriendlyNotFoundPage() throws Exception {
        when(expenseService.formFor(42L)).thenThrow(new ExpenseNotFoundException(42L));

        mockMvc.perform(get("/expenses/42/edit"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("expenses/not-found"))
                .andExpect(content().string(containsString("Expense not found")));
    }

    @Test
    void invalidMonthParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/expenses").param("month", "banana"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("not a valid value for month")));
    }

    @Test
    void exportReturnsCsvDownloadForTheFilter() throws Exception {
        Expense lunch = new Expense("Lunch, with \"Bob\"", new BigDecimal("85.00"), Category.FOOD, LocalDate.of(2026, 10, 1), null);
        when(expenseService.findAll(new ExpenseFilter(YearMonth.of(2026, 10), null)))
                .thenReturn(List.of(lunch));

        mockMvc.perform(get("/expenses/export").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"expenses-2026-10.csv\""))
                .andExpect(content().string("\uFEFFid,title,amount,category,date,note\r\n"
                        + "null,\"Lunch, with \"\"Bob\"\"\",85.00,FOOD,2026-10-01,\r\n"));
    }
}
