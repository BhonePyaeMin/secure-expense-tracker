package com.example.expenses;

import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.service.CategorySuggester;
import com.example.expenses.service.Csv;
import com.example.expenses.service.CsvExportService;
import com.example.expenses.service.CsvImportService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvImportServiceTest {

    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();

    private final CsvImportService importService =
            new CsvImportService(VALIDATION.getValidator(), new CategorySuggester());

    @AfterAll
    static void close() {
        VALIDATION.close();
    }

    @Test
    void parserHandlesQuotesCommasNewlinesAndBom() {
        String csv = "\uFEFFa,b,c\r\n\"x, y\",\"say \"\"hi\"\"\",\"line 1\nline 2\"\n\n1,2,3";

        assertThat(Csv.parse(csv)).containsExactly(
                List.of("a", "b", "c"),
                List.of("x, y", "say \"hi\"", "line 1\nline 2"),
                List.of("1", "2", "3"));
    }

    @Test
    void readsTheAppsOwnFormat() {
        CsvImportService.Result result = importService.read("""
                id,title,amount,category,date,note
                7,Lunch,85.00,FOOD,2026-10-01,
                8,"Rent, October","4,500",Rent,01/10/2026,Dorm
                """);

        assertThat(result.errors()).isEmpty();
        assertThat(result.expenses()).hasSize(2);
        ExpenseForm rent = result.expenses().get(1);
        assertThat(rent.getTitle()).isEqualTo("Rent, October");
        assertThat(rent.getAmount()).isEqualByComparingTo("4500");
        assertThat(rent.getCategory()).isEqualTo(Category.RENT);
        assertThat(rent.getDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(rent.getNote()).isEqualTo("Dorm");
    }

    @Test
    void missingCategoryIsGuessedFromTheTitleOrFallsBackToOther() {
        CsvImportService.Result result = importService.read("""
                Date,Description,Amount
                2026-10-01,Grab ride,120
                2026-10-02,Gift for mom,500
                """);

        assertThat(result.expenses()).extracting(ExpenseForm::getCategory)
                .containsExactly(Category.TRANSPORT, Category.OTHER);
    }

    @Test
    void anyInvalidRowRejectsTheWholeFileWithRowNumbers() {
        CsvImportService.Result result = importService.read("""
                title,amount,date
                Lunch,85,2026-10-01
                ,-5,2026-10-02
                Coffee,abc,yesterday
                Future,10,2999-01-01
                """);

        assertThat(result.isValid()).isFalse();
        assertThat(result.expenses()).isEmpty();
        assertThat(result.errors()).containsExactly(
                "Row 3: Amount must be greater than 0",
                "Row 3: Title is required",
                "Row 4: \"abc\" is not a valid amount",
                "Row 4: \"yesterday\" is not a valid date (use 2026-10-31 or 31/10/2026)",
                "Row 5: Date cannot be in the future");
    }

    @Test
    void paymentMethodColumnIsOptionalAndValidated() {
        CsvImportService.Result ok = importService.read("""
                title,amount,date,payment_method
                Lunch,85,2026-10-01,PromptPay
                Bus,15,2026-10-01,
                """);
        assertThat(ok.expenses()).extracting(ExpenseForm::getPaymentMethod)
                .containsExactly(com.example.expenses.model.PaymentMethod.PROMPTPAY, null);

        CsvImportService.Result bad = importService.read("title,amount,date,payment_method\nLunch,85,2026-10-01,Bitcoin\n");
        assertThat(bad.errors()).containsExactly("Row 2: \"Bitcoin\" is not a payment method");
    }

    @Test
    void missingRequiredColumnsAreReported() {
        CsvImportService.Result result = importService.read("name,price\nLunch,85\n");

        assertThat(result.errors()).singleElement().asString().contains("Missing: title, amount, date");
    }

    @Test
    void anExportedFileImportsBackUnchanged() throws Exception {
        Expense tricky = new Expense(null, "=SUM(A1) \"quoted\", with comma", new BigDecimal("12.34"),
                Category.FUN, LocalDate.of(2026, 9, 30), "line 1\nline 2");
        tricky.setPaymentMethod(com.example.expenses.model.PaymentMethod.EWALLET);
        StringWriter exported = new StringWriter();
        new CsvExportService().write(List.of(tricky), exported);

        CsvImportService.Result result = importService.read(exported.toString());

        assertThat(result.errors()).isEmpty();
        ExpenseForm back = result.expenses().get(0);
        assertThat(back.getTitle()).isEqualTo(tricky.getTitle());
        assertThat(back.getAmount()).isEqualByComparingTo(tricky.getAmount());
        assertThat(back.getCategory()).isEqualTo(Category.FUN);
        assertThat(back.getDate()).isEqualTo(tricky.getDate());
        assertThat(back.getNote()).isEqualTo(tricky.getNote());
        assertThat(back.getPaymentMethod()).isEqualTo(com.example.expenses.model.PaymentMethod.EWALLET);
    }
}
