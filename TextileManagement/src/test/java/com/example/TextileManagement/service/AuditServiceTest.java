package com.example.TextileManagement.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.repository.AuditEventRepository;

class AuditServiceTest {
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final CurrentCompanyContext companyContext = new CurrentCompanyContext();
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void cleanupStopsAtTheBoundedBatchLimit() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Boolean.class), anyLong()))
                .thenThrow(new IllegalStateException("not PostgreSQL"));
        when(jdbcTemplate.update(any(String.class), any(LocalDateTime.class), eq(500)))
                .thenReturn(500);
        AuditService service = new AuditService(repository, companyContext, 180, jdbcTemplate);

        service.deleteExpiredEvents();

        verify(jdbcTemplate, times(10)).update(any(String.class), any(LocalDateTime.class), eq(500));
    }
}
