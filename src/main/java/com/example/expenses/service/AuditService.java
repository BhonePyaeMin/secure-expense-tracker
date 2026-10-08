package com.example.expenses.service;

import com.example.expenses.model.AuditAction;
import com.example.expenses.model.AuditEntry;
import com.example.expenses.repository.AuditEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;
    private final Clock clock;

    public AuditService(AuditEntryRepository auditEntryRepository, Clock clock) {
        this.auditEntryRepository = auditEntryRepository;
        this.clock = clock;
    }

    /** Joins the caller's transaction, so the log entry is saved only if the change itself is saved. */
    @Transactional
    public void record(Long userId, AuditAction action, String details) {
        auditEntryRepository.save(new AuditEntry(userId, action, truncate(details), Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> findForUser(Long userId, Pageable pageable) {
        return auditEntryRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
    }

    private static String truncate(String details) {
        if (details == null || details.length() <= AuditEntry.MAX_DETAILS) {
            return details;
        }
        return details.substring(0, AuditEntry.MAX_DETAILS - 3) + "...";
    }
}
