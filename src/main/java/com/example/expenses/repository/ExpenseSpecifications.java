package com.example.expenses.repository;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import org.springframework.data.jpa.domain.Specification;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Building blocks for the expense list query. Each filter is optional, so the
 * WHERE clause is assembled from only the filters that are set. The owner
 * and not-in-trash conditions are always added.
 */
public final class ExpenseSpecifications {

    private static final char LIKE_ESCAPE = '!';

    private ExpenseSpecifications() {
    }

    public static Specification<Expense> ownedBy(Long ownerId) {
        return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    public static Specification<Expense> notInTrash() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Expense> inMonth(YearMonth month) {
        return (root, query, cb) -> cb.between(root.get("date"), month.atDay(1), month.atEndOfMonth());
    }

    public static Specification<Expense> hasCategory(Category category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    /** Case-insensitive "contains" on title or note. % and _ typed by the user are matched literally. */
    public static Specification<Expense> textContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("note")), pattern, LIKE_ESCAPE));
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public static Specification<Expense> matching(Long ownerId, ExpenseFilter filter) {
        List<Specification<Expense>> specs = new ArrayList<>();
        specs.add(ownedBy(ownerId));
        specs.add(notInTrash());
        if (filter.month() != null) {
            specs.add(inMonth(filter.month()));
        }
        if (filter.category() != null) {
            specs.add(hasCategory(filter.category()));
        }
        if (filter.q() != null) {
            specs.add(textContains(filter.q()));
        }
        return Specification.allOf(specs);
    }
}
