package com.example.TextileManagement.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.AuditEvent;
import com.example.TextileManagement.repository.AuditEventRepository;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    private final CurrentCompanyContext companyContext;
    private final int retentionDays;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean localCleanupLock = new AtomicBoolean();
    private static final long CLEANUP_LOCK_KEY = 7_421_903_117L;
    private static final int CLEANUP_BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_RUN = 10;

    public AuditService(AuditEventRepository repository, CurrentCompanyContext companyContext,
            @Value("${app.audit.retention-days:180}") int retentionDays, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.companyContext = companyContext;
        this.retentionDays = Math.max(30, retentionDays);
        this.jdbcTemplate = jdbcTemplate;
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
        if (!tryAcquireCleanupLock()) {
            return;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
                int deleted = jdbcTemplate.update("delete from audit_events where id in "
                        + "(select id from audit_events where created_at < ? order by id limit ?)", cutoff,
                        CLEANUP_BATCH_SIZE);
                if (deleted < CLEANUP_BATCH_SIZE) {
                    break;
                }
            }
        } finally {
            releaseCleanupLock();
        }
    }

    private boolean tryAcquireCleanupLock() {
        try {
            // Neon pooled connections use transaction pooling; session-level advisory locks do not survive it.
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class,
                    CLEANUP_LOCK_KEY));
        } catch (RuntimeException ignored) {
            // H2/local fallback; production Neon uses the cross-instance Postgres lock above.
            return localCleanupLock.compareAndSet(false, true);
        }
    }

    private void releaseCleanupLock() {
        // Transaction-level Postgres locks release at transaction end; this also releases the H2 fallback lock.
        localCleanupLock.set(false);
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
