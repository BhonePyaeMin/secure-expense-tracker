package com.example.expenses.dto;

import com.example.expenses.model.Category;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional filters for the expense list. A null field means "don't filter on this".
 *
 * @param q text to search for in the title or note
 */
public record ExpenseFilter(YearMonth month, Category category, String q) {

    private static final int MAX_SEARCH_LENGTH = 100;

    public ExpenseFilter {
        q = StringUtils.hasText(q) ? q.strip() : null;
        if (q != null && q.length() > MAX_SEARCH_LENGTH) {
            q = q.substring(0, MAX_SEARCH_LENGTH);
        }
    }

    public ExpenseFilter(YearMonth month, Category category) {
        this(month, category, null);
    }

    public boolean isEmpty() {
        return month == null && category == null && q == null;
    }

    /** Link to {@code path} that keeps this filter, e.g. "/expenses?month=2026-10&page=1". */
    public String url(String path, int page) {
        List<String> params = new ArrayList<>();
        if (month != null) {
            params.add("month=" + month);
        }
        if (category != null) {
            params.add("category=" + category.name());
        }
        if (q != null) {
            params.add("q=" + UriUtils.encode(q, StandardCharsets.UTF_8));
        }
        if (page > 0) {
            params.add("page=" + page);
        }
        return params.isEmpty() ? path : path + "?" + String.join("&", params);
    }

    public String url(String path) {
        return url(path, 0);
    }
}
