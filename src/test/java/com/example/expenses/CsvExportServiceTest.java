package com.example.expenses;

import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.service.Csv;
import com.example.expenses.service.CsvExportService;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportServiceTest {

    private final CsvExportService exportService = new CsvExportService();

    @Test
    void plainValuesAreNotQuoted() {
        assertThat(Csv.escape("Lunch")).isEqualTo("Lunch");
    }

    @Test
    void commasAreQuoted() {
        assertThat(Csv.escape("Rice, chicken")).isEqualTo("\"Rice, chicken\"");
    }

    @Test
    void quotesAreDoubledAndQuoted() {
        assertThat(Csv.escape("The \"good\" cafe")).isEqualTo("\"The \"\"good\"\" cafe\"");
    }

    @Test
    void lineBreaksAreQuoted() {
        assertThat(Csv.escape("line 1\nline 2")).isEqualTo("\"line 1\nline 2\"");
    }

    @Test
    void nullAndEmptyBecomeEmptyFields() {
        assertThat(Csv.escape(null)).isEmpty();
        assertThat(Csv.escape("")).isEmpty();
    }

    @Test
    void formulaLikeValuesAreNeutralized() {
        assertThat(Csv.escape("=HYPERLINK(\"http://evil\")")).isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(Csv.escape("+66 phone")).isEqualTo("'+66 phone");
        assertThat(Csv.escape("@sum")).isEqualTo("'@sum");
    }

    @Test
    void writesHeaderAndOneRowPerExpense() throws Exception {
        Expense expense = new Expense(null, "Lunch", new BigDecimal("85.50"), Category.FOOD, LocalDate.of(2026, 10, 1), "With \"Am\", Bo");
        StringWriter out = new StringWriter();

        exportService.write(List.of(expense), out);

        assertThat(out.toString()).isEqualTo("\uFEFF"
                + "id,title,amount,category,date,note\r\n"
                + "null,Lunch,85.50,FOOD,2026-10-01,\"With \"\"Am\"\", Bo\"\r\n");
    }
}
