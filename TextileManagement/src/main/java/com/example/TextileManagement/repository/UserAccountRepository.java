package com.example.TextileManagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.TextileManagement.entities.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);
}
