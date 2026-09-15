package com.example.TextileManagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.TextileManagement.service.WorkspaceCollaborationService;
import com.example.TextileManagement.service.WorkspaceCollaborationService.InvitationCreated;
import com.example.TextileManagement.service.WorkspaceCollaborationService.MemberSummary;
import com.example.TextileManagement.service.WorkspaceCollaborationService.WorkspaceSummary;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceCollaborationService collaborationService;

    public WorkspaceController(WorkspaceCollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @GetMapping
    public List<WorkspaceSummary> getWorkspaces(Authentication authentication) {
        return collaborationService.listWorkspaces(authentication.getName());
    }

    @GetMapping("/{workspaceId}/members")
    public PageResponse<MemberSummary> getMembers(@PathVariable Long workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {
        return PageResponse.from(collaborationService.listMembers(workspaceId, authentication.getName(), page, size));
    }

    @PostMapping("/{workspaceId}/invitations")
    public ResponseEntity<InvitationCreated> invite(@PathVariable Long workspaceId,
            @RequestBody InvitationRequest request, Authentication authentication) {
        InvitationCreated invitation = collaborationService.invite(workspaceId, authentication.getName(),
                request == null ? null : request.email(), request == null ? null : request.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    @PatchMapping("/{workspaceId}/members/{userId}/role")
    public ResponseEntity<Void> changeRole(@PathVariable Long workspaceId, @PathVariable Long userId,
            @RequestBody RoleRequest request, Authentication authentication) {
        collaborationService.changeRole(workspaceId, userId, authentication.getName(),
                request == null ? null : request.role());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long workspaceId, @PathVariable Long userId,
            Authentication authentication) {
        collaborationService.removeMember(workspaceId, userId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    public record InvitationRequest(String email, String role) {
    }

    public record RoleRequest(String role) {
    }
}
