package com.example.expenses.repository;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import org.springframework.data.jpa.domain.Specification;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Building blocks for the expense list query. Each filter is optional, so the
 * WHERE clause is assembled from only the filters that are set. The owner
 * condition is always added.
 */
public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    public static Specification<Expense> ownedBy(Long ownerId) {
        return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    public static Specification<Expense> inMonth(YearMonth month) {
        return (root, query, cb) -> cb.between(root.get("date"), month.atDay(1), month.atEndOfMonth());
    }

    public static Specification<Expense> hasCategory(Category category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Expense> matching(Long ownerId, ExpenseFilter filter) {
        List<Specification<Expense>> specs = new ArrayList<>();
        specs.add(ownedBy(ownerId));
        if (filter.month() != null) {
            specs.add(inMonth(filter.month()));
        }
        if (filter.category() != null) {
            specs.add(hasCategory(filter.category()));
        }
        return Specification.allOf(specs);
    }
}
