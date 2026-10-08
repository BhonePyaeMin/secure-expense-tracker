package com.example.expenses.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Something to save up for, like a laptop fund. Money is added and taken out by hand. */
@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "saved_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal savedAmount;

    // Optional: when the money is needed by
    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavingsGoal() {
    }

    public SavingsGoal(User owner, String name, BigDecimal targetAmount, BigDecimal savedAmount, LocalDate targetDate) {
        this.owner = owner;
        this.name = name;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.targetDate = targetDate;
        this.createdAt = Instant.now();
    }

    public void deposit(BigDecimal amount) {
        savedAmount = savedAmount.add(amount);
    }

    /** @throws IllegalArgumentException when taking out more than has been saved */
    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(savedAmount) > 0) {
            throw new IllegalArgumentException("Can't take out more than is saved");
        }
        savedAmount = savedAmount.subtract(amount);
    }

    /** Saved as a percentage of the target, e.g. 34.3; can go past 100. */
    public BigDecimal getPercent() {
        return Money.percent(savedAmount, targetAmount);
    }

    /** Like getPercent, but capped at 100 for the progress bar. */
    public BigDecimal getBarPercent() {
        return getPercent().min(HUNDRED);
    }

    public BigDecimal getRemaining() {
        return targetAmount.subtract(savedAmount).max(BigDecimal.ZERO);
    }

    public boolean isReached() {
        return savedAmount.compareTo(targetAmount) >= 0;
    }

    public boolean isOverdue(LocalDate today) {
        return targetDate != null && targetDate.isBefore(today) && !isReached();
    }

    /**
     * How much to save each month to reach the target by the target date, counting this month and the
     * target date's month, rounded with the app's money rules (Money). Null when there's no date,
     * the goal is reached, or the date has passed.
     */
    public BigDecimal monthlyNeeded(LocalDate today) {
        if (targetDate == null || isReached() || targetDate.isBefore(today)) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)) + 1;
        return Money.divide(getRemaining(), months);
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public BigDecimal getSavedAmount() {
        return savedAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
