package com.example.TextileManagement.service;

import org.springframework.stereotype.Service;

import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.WorkspaceMemberRepository;

@Service
public class WorkspaceRoleAccessService {
    private static final java.util.Set<String> COMPANY_SETTINGS_ROLES = java.util.Set.of("OWNER", "ADMIN");
    private final CompanyProfileRepository companyProfileRepository;
    private final WorkspaceMemberRepository memberRepository;

    public WorkspaceRoleAccessService(CompanyProfileRepository companyProfileRepository,
            WorkspaceMemberRepository memberRepository) {
        this.companyProfileRepository = companyProfileRepository;
        this.memberRepository = memberRepository;
    }

    public boolean canWriteCompany(String username, Long companyId) {
        return companyProfileRepository.findWorkspaceIdById(companyId)
                .flatMap(workspaceId -> memberRepository
                        .findByWorkspace_IdAndUser_UsernameIgnoreCase(workspaceId, username))
                .map(member -> member.getRole() != null && !"VIEWER".equalsIgnoreCase(member.getRole().trim()))
                .orElse(false);
    }

    public boolean canManageWorkspace(String username, Long workspaceId) {
        return memberRepository.findByWorkspace_IdAndUser_UsernameIgnoreCase(workspaceId, username)
                .map(member -> "OWNER".equalsIgnoreCase(member.getRole()) || "ADMIN".equalsIgnoreCase(member.getRole()))
                .orElse(false);
    }

    public boolean canManageCompanySettings(String username, Long companyId) {
        return companyProfileRepository.findWorkspaceIdById(companyId)
                .flatMap(workspaceId -> memberRepository
                        .findByWorkspace_IdAndUser_UsernameIgnoreCase(workspaceId, username))
                .map(member -> member.getRole() != null
                        && COMPANY_SETTINGS_ROLES.contains(member.getRole().trim().toUpperCase()))
                .orElse(false);
    }
}
