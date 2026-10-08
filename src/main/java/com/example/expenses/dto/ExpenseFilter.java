package com.example.expenses.dto;

import com.example.expenses.model.Category;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.YearMonth;

/**
 * Optional filters for the expense list. A null field means "don't filter on this".
 */
public record ExpenseFilter(YearMonth month, Category category) {

    public boolean isEmpty() {
        return month == null && category == null;
    }

    /** Link to {@code path} that keeps this filter, e.g. "/expenses?month=2026-10&page=1". */
    public String url(String path, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (month != null) {
            builder.queryParam("month", month);
        }
        if (category != null) {
            builder.queryParam("category", category);
        }
        if (page > 0) {
            builder.queryParam("page", page);
        }
        return builder.encode().build().toUriString();
    }

    public String url(String path) {
        return url(path, 0);
    }
}
