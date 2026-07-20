package com.example.TextileManagement.repository;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.TextileManagement.entities.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop100ByCompanyIdOrderByCreatedAtDesc(Long companyId);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
