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
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A template for an expense that repeats every month (rent, phone plan, subscriptions).
 * A scheduled job turns it into a real {@link Expense} on each due date.
 */
@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private Category category;

    @Column(length = 200)
    private String note;

    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    // The day it was first due, e.g. 31. Shorter months use their last day instead.
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(nullable = false)
    private boolean active;

    protected RecurringExpense() {
    }

    public RecurringExpense(User owner, String title, BigDecimal amount, Category category, String note,
                            PaymentMethod paymentMethod, LocalDate firstDueDate) {
        this.owner = owner;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.paymentMethod = paymentMethod;
        this.dayOfMonth = firstDueDate.getDayOfMonth();
        this.nextDueDate = firstDueDate;
        this.active = true;
    }

    public boolean isDue(LocalDate today) {
        return active && !nextDueDate.isAfter(today);
    }

    /** Moves to next month's due date: the 31st becomes the 30th or 28th in shorter months, then the 31st again. */
    public void advance() {
        YearMonth next = YearMonth.from(nextDueDate).plusMonths(1);
        nextDueDate = next.atDay(Math.min(dayOfMonth, next.lengthOfMonth()));
    }

    public void pause() {
        active = false;
    }

    /** Resumes without back-filling the months it was paused. */
    public void resume(LocalDate today) {
        active = true;
        while (nextDueDate.isBefore(today)) {
            advance();
        }
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Category getCategory() {
        return category;
    }

    public String getNote() {
        return note;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public boolean isActive() {
        return active;
    }
}
