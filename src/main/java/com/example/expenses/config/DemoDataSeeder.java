package com.example.expenses.config;

import com.example.expenses.model.Budget;
import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.model.Income;
import com.example.expenses.model.PaymentMethod;
import com.example.expenses.model.SavingsGoal;
import com.example.expenses.model.User;
import com.example.expenses.repository.BudgetRepository;
import com.example.expenses.repository.ExpenseRepository;
import com.example.expenses.repository.IncomeRepository;
import com.example.expenses.repository.SavingsGoalRepository;
import com.example.expenses.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a "demo" account with sample data. Only runs with the "demo" profile:
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
 * Sign in as demo / demo1234.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_USERNAME = "demo";
    static final String DEMO_PASSWORD = "demo1234";

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

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final IncomeRepository incomeRepository;
    private final SavingsGoalRepository goalRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(UserRepository userRepository, ExpenseRepository expenseRepository,
                          BudgetRepository budgetRepository, IncomeRepository incomeRepository,
                          SavingsGoalRepository goalRepository, PasswordEncoder passwordEncoder,
                          Clock clock) {
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
        this.incomeRepository = incomeRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User demo = userRepository.findByUsername(DEMO_USERNAME)
                .orElseGet(() -> userRepository.save(new User(DEMO_USERNAME, passwordEncoder.encode(DEMO_PASSWORD))));
        LocalDate today = LocalDate.now(clock);
        seedIncome(demo, today);
        if (goalRepository.countByOwnerId(demo.getId()) == 0) {
            goalRepository.save(new SavingsGoal(demo, "New laptop", new BigDecimal("35000.00"),
                    new BigDecimal("12000.00"), today.plusMonths(5).withDayOfMonth(1).minusDays(1)));
        }
        if (expenseRepository.countByOwnerId(demo.getId()) > 0) {
            log.info("Demo profile: demo account already has data, skipping seed");
            return;
        }
        List<Expense> expenses = new ArrayList<>();
        PaymentMethod[] methods = {PaymentMethod.CASH, PaymentMethod.PROMPTPAY, PaymentMethod.EWALLET,
                PaymentMethod.CASH, PaymentMethod.CARD};
        for (int i = 0; i < SAMPLES.length; i++) {
            Object[] sample = SAMPLES[i];
            Expense expense = new Expense(demo, (String) sample[1], new BigDecimal((String) sample[2]),
                    (Category) sample[3], today.minusDays((Integer) sample[0]), (String) sample[4]);
            expense.setPaymentMethod(sample[3] == Category.RENT ? PaymentMethod.BANK : methods[i % methods.length]);
            expenses.add(expense);
        }
        expenseRepository.saveAll(expenses);
        budgetRepository.saveAll(List.of(
                new Budget(demo, Category.FOOD, new BigDecimal("3000.00")),
                new Budget(demo, Category.TRANSPORT, new BigDecimal("1000.00")),
                new Budget(demo, Category.FUN, new BigDecimal("500.00"))));
        log.info("Demo profile: seeded {} expenses and 3 budgets. Sign in as {} / {}",
                expenses.size(), DEMO_USERNAME, DEMO_PASSWORD);
    }

    // Separate from the expenses so a demo database from before income existed gets some too
    private void seedIncome(User demo, LocalDate today) {
        if (incomeRepository.countByOwnerId(demo.getId()) > 0) {
            return;
        }
        LocalDate thisMonth = today.withDayOfMonth(1);
        LocalDate lastMonth = thisMonth.minusMonths(1);
        incomeRepository.saveAll(List.of(
                new Income(demo, new BigDecimal("8000.00"), "Allowance from parents", lastMonth),
                new Income(demo, new BigDecimal("4500.00"), "Part-time job", lastMonth.plusDays(14)),
                new Income(demo, new BigDecimal("8000.00"), "Allowance from parents", thisMonth)));
    }
}
