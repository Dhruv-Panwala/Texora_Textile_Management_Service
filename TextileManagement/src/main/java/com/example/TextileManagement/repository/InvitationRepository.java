package com.example.TextileManagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.TextileManagement.entities.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByWorkspace_IdAndEmailIgnoreCaseAndAcceptedAtIsNull(Long workspaceId, String email);
}
