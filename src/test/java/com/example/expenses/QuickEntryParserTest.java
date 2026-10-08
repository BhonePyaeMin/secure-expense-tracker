package com.example.expenses;

import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.Category;
import com.example.expenses.service.CategorySuggester;
import com.example.expenses.service.QuickEntryParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class QuickEntryParserTest {

    // A Thursday
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private final QuickEntryParser parser = new QuickEntryParser(new CategorySuggester());

    @Test
    void lunchEightyFiveBahtYesterday() {
        ExpenseForm form = parser.parse("lunch 85 baht yesterday", TODAY);

        assertThat(form.getTitle()).isEqualTo("Lunch");
        assertThat(form.getAmount()).isEqualByComparingTo("85");
        assertThat(form.getDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(form.getCategory()).isEqualTo(Category.FOOD);
    }

    @Test
    void amountInTheMiddleAndNoDateMeansToday() {
        ExpenseForm form = parser.parse("grab 120 to campus", TODAY);

        assertThat(form.getTitle()).isEqualTo("Grab to campus");
        assertThat(form.getAmount()).isEqualByComparingTo("120");
        assertThat(form.getDate()).isEqualTo(TODAY);
        assertThat(form.getCategory()).isEqualTo(Category.TRANSPORT);
    }

    @Test
    void bahtSignDecimalsAndDaysAgo() {
        ExpenseForm form = parser.parse("coffee ฿55.50 3 days ago", TODAY);

        assertThat(form.getTitle()).isEqualTo("Coffee");
        assertThat(form.getAmount()).isEqualByComparingTo("55.50");
        assertThat(form.getDate()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void dayMonthDateIsNotMistakenForTheAmount() {
        ExpenseForm form = parser.parse("rent 4,500 1/10", TODAY);

        assertThat(form.getTitle()).isEqualTo("Rent");
        assertThat(form.getAmount()).isEqualByComparingTo("4500");
        assertThat(form.getDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(form.getCategory()).isEqualTo(Category.RENT);
    }

    @Test
    void dateWithoutYearInTheFutureMeansLastYear() {
        assertThat(parser.parse("gift 500 25/12", TODAY).getDate()).isEqualTo(LocalDate.of(2025, 12, 25));
        assertThat(parser.parse("gift 500 dec 25", TODAY).getDate()).isEqualTo(LocalDate.of(2025, 12, 25));
        assertThat(parser.parse("books 300 5 oct", TODAY).getDate()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void weekdayMeansTheMostRecentOne() {
        assertThat(parser.parse("netflix 419 monday", TODAY).getDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(parser.parse("snacks 40 thursday", TODAY).getDate()).isEqualTo(TODAY);
        assertThat(parser.parse("snacks 40 last thursday", TODAY).getDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void numbersInsideWordsAreNotAmounts() {
        ExpenseForm form = parser.parse("7-11 snacks 45", TODAY);

        assertThat(form.getTitle()).isEqualTo("7-11 snacks");
        assertThat(form.getAmount()).isEqualByComparingTo("45");
    }

    @Test
    void fillerWordsAtTheEdgesAreDropped() {
        ExpenseForm form = parser.parse("85 for dinner on friday", TODAY);

        assertThat(form.getTitle()).isEqualTo("Dinner");
        assertThat(form.getDate()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void thaiWordsWork() {
        ExpenseForm form = parser.parse("ข้าวมันไก่ 50 บาท เมื่อวาน", TODAY);

        assertThat(form.getTitle()).isEqualTo("ข้าวมันไก่");
        assertThat(form.getAmount()).isEqualByComparingTo("50");
        assertThat(form.getDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(form.getCategory()).isEqualTo(Category.FOOD);
    }

    @Test
    void textWithoutAnAmountLeavesItEmptyForTheUser() {
        ExpenseForm form = parser.parse("something", TODAY);

        assertThat(form.getTitle()).isEqualTo("Something");
        assertThat(form.getAmount()).isNull();
        assertThat(form.getCategory()).isNull();
        assertThat(form.getDate()).isEqualTo(TODAY);
    }

    @Test
    void impossibleDatesAreLeftAsText() {
        ExpenseForm form = parser.parse("thing 31/02 10", TODAY);

        assertThat(form.getDate()).isEqualTo(TODAY);
    }
}
