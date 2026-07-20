package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.entities.Invitation;
import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.entities.Workspace;
import com.example.TextileManagement.entities.WorkspaceMember;
import com.example.TextileManagement.repository.InvitationRepository;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.UserAccountRepository;
import com.example.TextileManagement.repository.WorkspaceMemberRepository;
import com.example.TextileManagement.repository.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
class WorkspaceCollaborationServiceTest {
    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MailService mailService;

    @Mock
    private CompanyProfileRepository companyProfileRepository;

    private Workspace workspace;
    private WorkspaceMember ownerMembership;
    private WorkspaceCollaborationService service;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setId(10L);
        workspace.setName("Textile Group");
        UserAccount owner = new UserAccount();
        owner.setId(20L);
        owner.setUsername("owner@example.com");
        ownerMembership = new WorkspaceMember();
        ownerMembership.setWorkspace(workspace);
        ownerMembership.setUser(owner);
        ownerMembership.setRole("OWNER");
        service = new WorkspaceCollaborationService(workspaceRepository, memberRepository, userRepository,
                invitationRepository, passwordEncoder, mailService, companyProfileRepository);
    }

    @Test
    void invitationStoresOnlyAHashAndAllowsAnInvitableRole() {
        when(memberRepository.findByWorkspace_IdAndUser_UsernameIgnoreCase(10L, "owner@example.com"))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.empty());
        when(invitationRepository.findByWorkspace_IdAndEmailIgnoreCaseAndAcceptedAtIsNull(10L, "staff@example.com"))
                .thenReturn(Optional.empty());
        AtomicReference<Invitation> stored = new AtomicReference<>();
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
            Invitation saved = invocation.getArgument(0);
            stored.set(saved);
            saved.setId(1L);
            return saved;
        });

        WorkspaceCollaborationService.InvitationCreated invitation = service.invite(
                10L, "owner@example.com", "staff@example.com", "staff");

        assertNotEquals(invitation.token(), stored.get().getTokenHash());
        assertTrue(invitation.token().length() > 20);
    }

    @Test
    void nonManagerCannotCreateAnInvitation() {
        ownerMembership.setRole("STAFF");
        when(memberRepository.findByWorkspace_IdAndUser_UsernameIgnoreCase(10L, "owner@example.com"))
                .thenReturn(Optional.of(ownerMembership));

        assertThrows(ResponseStatusException.class,
                () -> service.invite(10L, "owner@example.com", "staff@example.com", "STAFF"));
    }

    @Test
    void invitingSamePendingEmailReissuesTheLink() {
        when(memberRepository.findByWorkspace_IdAndUser_UsernameIgnoreCase(10L, "owner@example.com"))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.empty());
        Invitation active = new Invitation();
        active.setId(5L);
        active.setTokenHash("old-hash");
        when(invitationRepository.findByWorkspace_IdAndEmailIgnoreCaseAndAcceptedAtIsNull(10L, "staff@example.com"))
                .thenReturn(Optional.of(active));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceCollaborationService.InvitationCreated invitation = service.invite(
                10L, "owner@example.com", "staff@example.com", "viewer");

        assertNotEquals("old-hash", active.getTokenHash());
        assertNotEquals(invitation.token(), active.getTokenHash());
        assertTrue(invitation.token().length() > 20);
    }
}
