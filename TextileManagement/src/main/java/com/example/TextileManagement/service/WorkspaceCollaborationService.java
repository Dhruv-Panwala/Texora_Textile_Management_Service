package com.example.TextileManagement.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.entities.Invitation;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.entities.Workspace;
import com.example.TextileManagement.entities.WorkspaceMember;
import com.example.TextileManagement.repository.InvitationRepository;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.UserAccountRepository;
import com.example.TextileManagement.repository.WorkspaceMemberRepository;
import com.example.TextileManagement.repository.WorkspaceRepository;

@Service
public class WorkspaceCollaborationService {
    private static final Set<String> MANAGER_ROLES = Set.of("OWNER", "ADMIN");
    private static final Set<String> INVITABLE_ROLES = Set.of("ADMIN", "MANAGER", "STAFF", "ACCOUNTANT", "VIEWER");
    private static final int INVITATION_DAYS = 7;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserAccountRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOutboxService outboxService;
    private final CompanyProfileRepository companyProfileRepository;

    public WorkspaceCollaborationService(WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository, UserAccountRepository userRepository,
            InvitationRepository invitationRepository, PasswordEncoder passwordEncoder,
            CompanyProfileRepository companyProfileRepository, EmailOutboxService outboxService) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxService = outboxService;
        this.companyProfileRepository = companyProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceSummary> listWorkspaces(String username) {
        return workspaceRepository.findAccessibleWithRoleByUsername(username).stream()
                .map(workspace -> new WorkspaceSummary(workspace.getId(), workspace.getName(),
                        workspace.getRole() == null ? "VIEWER" : workspace.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MemberSummary> listMembers(Long workspaceId, String username, int page, int size) {
        requireMember(workspaceId, username);
        int boundedSize = Math.min(Math.max(size, 1), 100);
        return memberRepository.findAllByWorkspace_Id(workspaceId,
                PageRequest.of(Math.max(page, 0), boundedSize, Sort.by("createdAt").ascending().and(Sort.by("id").ascending())))
                .map(member -> new MemberSummary(member.getUser().getId(), member.getUser().getUsername(),
                        member.getUser().getEmail(), member.getUser().getDisplayName(), member.getRole(),
                        member.getCreatedAt()));
    }

    @Transactional
    public InvitationCreated invite(Long workspaceId, String username, String email, String role) {
        WorkspaceMember manager = requireManager(workspaceId, username);
        Workspace workspace = manager.getWorkspace();
        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = normalizeRole(role);
        if (!validEmail(normalizedEmail) || !INVITABLE_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email and role are required");
        }
        UserAccount existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existingUser != null && memberRepository.findByWorkspace_IdAndUser_Id(workspaceId, existingUser.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a workspace member");
        }
        String token = UUID.randomUUID() + UUID.randomUUID().toString();
        Invitation invitation = invitationRepository
                .findByWorkspace_IdAndEmailIgnoreCaseAndAcceptedAtIsNull(workspaceId, normalizedEmail)
                .orElseGet(Invitation::new);
        invitation.setWorkspace(workspace);
        invitation.setEmail(normalizedEmail);
        invitation.setRole(normalizedRole);
        invitation.setTokenHash(hashToken(token));
        invitation.setExpiresAt(LocalDateTime.now().plusDays(INVITATION_DAYS));
        invitation.setInvitedBy(manager.getUser());
        invitation = invitationRepository.save(invitation);
        String businessNames = companyProfileRepository.findAllByWorkspace_IdOrderByTradeNameAsc(workspaceId).stream()
                .map(CompanyProfile::getTradeName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        String invitationWorkspaceName = businessNames.isBlank() ? workspace.getName() : businessNames;
        outboxService.enqueueInvitation(normalizedEmail, invitationWorkspaceName, normalizedRole, token);
        return new InvitationCreated(invitation.getId(), normalizedEmail, normalizedRole,
                invitation.getExpiresAt(), token);
    }

    @Transactional
    public void changeRole(Long workspaceId, Long userId, String requesterUsername, String role) {
        requireManager(workspaceId, requesterUsername);
        String normalizedRole = normalizeRole(role);
        if (!INVITABLE_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid member role");
        }
        WorkspaceMember member = memberRepository.findByWorkspace_IdAndUser_Id(workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if ("OWNER".equals(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The workspace owner role cannot be changed");
        }
        member.setRole(normalizedRole);
    }

    @Transactional
    public void removeMember(Long workspaceId, Long userId, String requesterUsername) {
        requireManager(workspaceId, requesterUsername);
        WorkspaceMember member = memberRepository.findByWorkspace_IdAndUser_Id(workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if ("OWNER".equals(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The workspace owner cannot be removed");
        }
        memberRepository.delete(member);
    }

    @Transactional
    public UserAccount acceptAsNewUser(String token, String displayName, String password) {
        Invitation invitation = validInvitation(token);
        if (userRepository.findByEmailIgnoreCase(invitation.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this invitation");
        }
        if (password == null || password.length() < 12 || displayName == null || displayName.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name and a 12-character password are required");
        }
        UserAccount user = new UserAccount();
        user.setUsername(invitation.getEmail());
        user.setEmail(invitation.getEmail());
        user.setDisplayName(displayName.trim());
        user.setStatus("ACTIVE");
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);
        addMembership(invitation, user);
        invitation.setAcceptedAt(LocalDateTime.now());
        return user;
    }

    @Transactional
    public void acceptAsExistingUser(String token, String username) {
        Invitation invitation = validInvitation(token);
        UserAccount user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation email does not match this account");
        }
        addMembership(invitation, user);
        invitation.setAcceptedAt(LocalDateTime.now());
    }

    private WorkspaceMember requireManager(Long workspaceId, String username) {
        WorkspaceMember member = requireMember(workspaceId, username);
        if (!MANAGER_ROLES.contains(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace manager access is required");
        }
        return member;
    }

    private WorkspaceMember requireMember(Long workspaceId, String username) {
        return memberRepository.findByWorkspace_IdAndUser_UsernameIgnoreCase(workspaceId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"));
    }

    private Invitation validInvitation(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token is required");
        }
        Invitation invitation = invitationRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid or expired"));
        if (invitation.getAcceptedAt() != null || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid or expired");
        }
        return invitation;
    }

    private void addMembership(Invitation invitation, UserAccount user) {
        if (memberRepository.findByWorkspace_IdAndUser_Id(invitation.getWorkspace().getId(), user.getId()).isEmpty()) {
            WorkspaceMember membership = new WorkspaceMember();
            membership.setWorkspace(invitation.getWorkspace());
            membership.setUser(user);
            membership.setRole(invitation.getRole());
            memberRepository.save(membership);
        }
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeRole(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean validEmail(String value) {
        return value.length() <= 254 && value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to secure invitation", ex);
        }
    }

    public record WorkspaceSummary(Long id, String name, String role) {
    }

    public record MemberSummary(Long userId, String username, String email, String displayName, String role,
            LocalDateTime createdAt) {
    }

    public record InvitationCreated(Long id, String email, String role, LocalDateTime expiresAt, String token) {
    }
}
