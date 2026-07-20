package com.example.TextileManagement.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.AuditEvent;
import com.example.TextileManagement.repository.AuditEventRepository;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.service.WorkspaceRoleAccessService;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditEventRepository auditRepository;
    private final CompanyProfileRepository companyRepository;
    private final CurrentCompanyContext companyContext;
    private final WorkspaceRoleAccessService roleAccessService;

    public AuditController(AuditEventRepository auditRepository, CompanyProfileRepository companyRepository,
            CurrentCompanyContext companyContext, WorkspaceRoleAccessService roleAccessService) {
        this.auditRepository = auditRepository;
        this.companyRepository = companyRepository;
        this.companyContext = companyContext;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping
    public List<AuditRecord> getAudit(Authentication authentication) {
        Long companyId = companyContext.getCompanyId();
        if (companyId == null || authentication == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner or admin access is required");
        }
        Long workspaceId = companyRepository.findWorkspaceIdById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        if (!roleAccessService.canManageWorkspace(authentication.getName(), workspaceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner or admin access is required");
        }
        return auditRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(event -> new AuditRecord(event.getId(), event.getActorUsername(), event.getMethod(),
                        event.getPath(), event.getStatusCode(), event.getIpAddress(), event.getCreatedAt()))
                .toList();
    }

    public record AuditRecord(Long id, String actorUsername, String method, String path, int statusCode,
            String ipAddress, LocalDateTime createdAt) {
    }
}
