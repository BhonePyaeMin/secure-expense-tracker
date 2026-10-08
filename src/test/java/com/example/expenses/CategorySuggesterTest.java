package com.example.expenses;

import com.example.expenses.model.Category;
import com.example.expenses.service.CategorySuggester;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CategorySuggesterTest {

    private final CategorySuggester suggester = new CategorySuggester();

    @ParameterizedTest
    @CsvSource({
            "grab ride, TRANSPORT",
            "Grab ride to lunch, TRANSPORT",
            "GrabFood dinner, FOOD",
            "Lunch at cafe, FOOD",
            "Iced tea, FOOD",
            "Dinner and noodles, FOOD",
            "Monthly rent, RENT",
            "Water bill, RENT",
            "Netflix, FUN",
            "Movies with friends, FUN",
            "Textbooks, STUDY",
            "New notebook, STUDY",
            "Pharmacy, HEALTH",
            "BTS top-up, TRANSPORT",
            "7-Eleven snacks, FOOD",
            "ข้าวมันไก่, FOOD",
            "ค่าหอ ตุลาคม, RENT",
            "ค่าน้ำมัน, TRANSPORT",
    })
    void suggestsCategoryFromKeywords(String title, Category expected) {
        assertThat(suggester.suggest(title)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Birthday gift for mom", "Mushroom", "Barbell"})
    void noSuggestionWithoutAWholeWordMatch(String title) {
        assertThat(suggester.suggest(title)).isEmpty();
    }
}
