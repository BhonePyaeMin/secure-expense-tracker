package com.example.expenses.config;

import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty database with sample data. Only runs with the "demo" profile:
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    // days ago, title, amount, category, note
    private static final Object[][] SAMPLES = {
            {0, "Lunch at food court", "65.00", Category.FOOD, null},
            {0, "Grab ride to campus", "89.00", Category.TRANSPORT, null},
            {1, "Iced tea", "25.00", Category.FOOD, null},
            {1, "Printing lecture notes", "40.00", Category.STUDY, null},
            {2, "BTS top-up", "200.00", Category.TRANSPORT, "Rabbit card"},
            {3, "Movie night", "220.00", Category.FUN, "With roommates"},
            {4, "Groceries", "412.50", Category.FOOD, "7-Eleven and market"},
            {5, "Pharmacy", "135.00", Category.HEALTH, "Cold medicine"},
            {6, "Dinner, noodles", "60.00", Category.FOOD, null},
            {8, "Python course", "399.00", Category.STUDY, "Online, \"Data Science\" track"},
            {9, "Coffee", "55.00", Category.FOOD, null},
            {11, "Spotify", "69.00", Category.FUN, null},
            {13, "Bus fare", "15.00", Category.TRANSPORT, null},
            {15, "Monthly rent", "4500.00", Category.RENT, "Dorm, room 304"},
            {18, "Breakfast", "45.00", Category.FOOD, null},
            {21, "Gym day pass", "100.00", Category.HEALTH, null},
            {24, "Notebook and pens", "85.00", Category.STUDY, null},
            {27, "Birthday dinner", "650.00", Category.FOOD, "Shared bill"},
            {33, "Lunch", "70.00", Category.FOOD, null},
            {36, "Concert ticket", "1200.00", Category.FUN, null},
            {40, "Grab ride", "120.00", Category.TRANSPORT, null},
            {45, "Monthly rent", "4500.00", Category.RENT, "Dorm, room 304"},
            {50, "Textbook", "560.00", Category.STUDY, null},
            {55, "Dentist", "800.00", Category.HEALTH, "Cleaning"},
            {60, "Groceries", "380.00", Category.FOOD, null},
            {75, "Monthly rent", "4500.00", Category.RENT, "Dorm, room 304"},
    };

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;

    public DemoDataSeeder(ExpenseRepository expenseRepository, BudgetRepository budgetRepository) {
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (expenseRepository.count() > 0) {
            log.info("Demo profile: database already has data, skipping seed");
            return;
        }
        LocalDate today = LocalDate.now();
        List<Expense> expenses = new ArrayList<>();
        for (Object[] sample : SAMPLES) {
            expenses.add(new Expense((String) sample[1], new BigDecimal((String) sample[2]), (Category) sample[3],
                    today.minusDays((Integer) sample[0]), (String) sample[4]));
        }
        expenseRepository.saveAll(expenses);
        budgetRepository.saveAll(List.of(
                new Budget(Category.FOOD, new BigDecimal("3000.00")),
                new Budget(Category.TRANSPORT, new BigDecimal("1000.00")),
                new Budget(Category.FUN, new BigDecimal("500.00"))));
        log.info("Demo profile: seeded {} expenses and 3 budgets", expenses.size());
    }
}
