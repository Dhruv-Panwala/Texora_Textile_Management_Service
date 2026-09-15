package com.example.TextileManagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.TextileManagement.entities.WorkspaceMember;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    Optional<WorkspaceMember> findByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);

    Optional<WorkspaceMember> findByWorkspace_IdAndUser_UsernameIgnoreCase(Long workspaceId, String username);

    Page<WorkspaceMember> findAllByWorkspace_Id(Long workspaceId, Pageable pageable);

    void deleteByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);
}
