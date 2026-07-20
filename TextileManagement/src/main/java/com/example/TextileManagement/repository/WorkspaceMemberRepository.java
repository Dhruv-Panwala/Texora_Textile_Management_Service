package com.example.TextileManagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.TextileManagement.entities.WorkspaceMember;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    Optional<WorkspaceMember> findByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);

    Optional<WorkspaceMember> findByWorkspace_IdAndUser_UsernameIgnoreCase(Long workspaceId, String username);

    List<WorkspaceMember> findAllByWorkspace_IdOrderByCreatedAtAsc(Long workspaceId);

    void deleteByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);
}
