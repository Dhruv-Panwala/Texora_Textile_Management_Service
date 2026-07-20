package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.entities.WorkspaceMember;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.WorkspaceMemberRepository;

@ExtendWith(MockitoExtension.class)
class WorkspaceRoleAccessServiceTest {
    @Mock
    private CompanyProfileRepository companyProfileRepository;

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Test
    void viewerIsReadOnlyButStaffCanWrite() {
        WorkspaceRoleAccessService service = new WorkspaceRoleAccessService(companyProfileRepository, memberRepository);
        UserAccount user = new UserAccount();
        user.setUsername("member@example.com");
        WorkspaceMember membership = new WorkspaceMember();
        membership.setUser(user);
        when(companyProfileRepository.findWorkspaceIdById(50L)).thenReturn(Optional.of(10L));
        when(memberRepository.findByWorkspace_IdAndUser_UsernameIgnoreCase(10L, "member@example.com"))
                .thenReturn(Optional.of(membership));

        membership.setRole("VIEWER");
        assertFalse(service.canWriteCompany("member@example.com", 50L));
        membership.setRole("STAFF");
        assertTrue(service.canWriteCompany("member@example.com", 50L));
    }

    @Test
    void missingRoleIsReadOnly() {
        WorkspaceRoleAccessService service = new WorkspaceRoleAccessService(companyProfileRepository, memberRepository);
        WorkspaceMember membership = new WorkspaceMember();
        UserAccount user = new UserAccount();
        membership.setUser(user);
        when(companyProfileRepository.findWorkspaceIdById(50L)).thenReturn(Optional.of(10L));
        when(memberRepository.findByWorkspace_IdAndUser_UsernameIgnoreCase(10L, "member@example.com"))
                .thenReturn(Optional.of(membership));

        membership.setRole(null);

        assertFalse(service.canWriteCompany("member@example.com", 50L));
    }

    @Test
    void onlyOwnersAndAdminsCanManageAWorkspace() {
        WorkspaceRoleAccessService service = new WorkspaceRoleAccessService(companyProfileRepository, memberRepository);
        WorkspaceMember membership = new WorkspaceMember();
        when(memberRepository.findByWorkspace_IdAndUser_UsernameIgnoreCase(10L, "member@example.com"))
                .thenReturn(Optional.of(membership));

        membership.setRole("MANAGER");
        assertFalse(service.canManageWorkspace("member@example.com", 10L));
        membership.setRole("ADMIN");
        assertTrue(service.canManageWorkspace("member@example.com", 10L));
    }
}
