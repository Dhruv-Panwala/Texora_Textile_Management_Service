package com.example.TextileManagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.TextileManagement.entities.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

    void deleteByUser_Id(Long userId);
}
