package com.example.expenses.repository;

import com.example.expenses.model.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    Page<AuditEntry> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}
