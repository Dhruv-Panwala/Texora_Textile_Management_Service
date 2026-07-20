package com.example.TextileManagement.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.AuditEvent;
import com.example.TextileManagement.repository.AuditEventRepository;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    private final CurrentCompanyContext companyContext;
    private final int retentionDays;

    public AuditService(AuditEventRepository repository, CurrentCompanyContext companyContext,
            @Value("${app.audit.retention-days:180}") int retentionDays) {
        this.repository = repository;
        this.companyContext = companyContext;
        this.retentionDays = Math.max(30, retentionDays);
    }

    public void record(String method, String path, int statusCode, String ipAddress) {
        AuditEvent event = new AuditEvent();
        event.setCompanyId(companyContext.getCompanyId());
        event.setActorUsername(actorUsername());
        event.setMethod(limit(method, 10));
        event.setPath(limit(path, 300));
        event.setStatusCode(statusCode);
        event.setIpAddress(limit(ipAddress, 64));
        repository.save(event);
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void deleteExpiredEvents() {
        repository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(retentionDays));
    }

    private String actorUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return limit(authentication.getName(), 254);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
