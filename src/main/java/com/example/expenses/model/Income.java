package com.example.expenses.model;

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

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money coming in (salary, allowance, a part-time job), used for the monthly balance. */
@Entity
@Table(name = "incomes", indexes = @Index(name = "idx_incomes_owner_date", columnList = "owner_id, income_date"))
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "income_date", nullable = false)
    private LocalDate date;

    protected Income() {
    }

    public Income(User owner, BigDecimal amount, String source, LocalDate date) {
        this.owner = owner;
        this.amount = amount;
        this.source = source;
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getDate() {
        return date;
    }
}
