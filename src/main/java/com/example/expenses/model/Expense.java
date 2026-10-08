package com.example.expenses.model;

import com.example.expenses.config.TimeConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses",
        indexes = @Index(name = "idx_expenses_owner_date", columnList = "owner_id, expense_date"),
        // A recurring expense can create at most one expense per due date, even if two runs overlap
        uniqueConstraints = @UniqueConstraint(name = "uk_expenses_recurring_date",
                columnNames = {"recurring_expense_id", "expense_date"}))
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Every query filters on this, so users only ever see their own expenses
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private Category category;

    // "date" is a risky column name in SQL, so map it explicitly
    @Column(name = "expense_date", nullable = false)
    private LocalDate date;

    @Column(length = 255)
    private String note;

    // Null for expenses recorded before payment methods existed (shown as "Not set")
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    // Set when created by a RecurringExpense; null for everything typed in or imported
    @Column(name = "recurring_expense_id")
    private Long recurringExpenseId;

    // Soft delete: set when moved to the trash, null otherwise. Trashed expenses are left out of every list and total.
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Expense() {
    }

    public Expense(User owner, String title, BigDecimal amount, Category category, LocalDate date, String note) {
        this.owner = owner;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.date = date;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void moveToTrash(Instant now) {
        deletedAt = now;
    }

    public void restore() {
        deletedAt = null;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /** When it was moved to the trash, in the app's time zone (Bangkok), for display. */
    public LocalDateTime getDeletedAtLocal() {
        return deletedAt == null ? null : LocalDateTime.ofInstant(deletedAt, TimeConfig.ZONE);
    }

    public Long getRecurringExpenseId() {
        return recurringExpenseId;
    }

    public void setRecurringExpenseId(Long recurringExpenseId) {
        this.recurringExpenseId = recurringExpenseId;
    }
}
