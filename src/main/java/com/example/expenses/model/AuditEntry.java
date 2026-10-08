package com.example.expenses.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** One line of the audit log: who did what, and when. Entries are only ever added, never changed. */
@Entity
@Table(name = "audit_log", indexes = @Index(name = "idx_audit_user_time", columnList = "user_id, created_at"))
public class AuditEntry {

    public static final int MAX_DETAILS = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Column(length = MAX_DETAILS)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEntry() {
    }

    public AuditEntry(Long userId, AuditAction action, String details, Instant createdAt) {
        this.userId = userId;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The timestamp in the server's time zone, for display. */
    public LocalDateTime getLocalTime() {
        return LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault());
    }
}
