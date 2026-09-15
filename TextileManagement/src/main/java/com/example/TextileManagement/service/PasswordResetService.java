package com.example.TextileManagement.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.TextileManagement.entities.PasswordResetToken;
import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.repository.PasswordResetTokenRepository;
import com.example.TextileManagement.repository.UserAccountRepository;

@Service
public class PasswordResetService {
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_MINUTES = 15;

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserAccountRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOutboxService outboxService;

    public PasswordResetService(UserAccountRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailOutboxService outboxService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxService = outboxService;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!validEmail(normalizedEmail)) {
            return;
        }

        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .ifPresent(user -> {
                    tokenRepository.deleteByUser_Id(user.getId());
                    String token = createToken();
                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setUser(user);
                    resetToken.setTokenHash(hashToken(token));
                    resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_MINUTES));
                    tokenRepository.save(resetToken);
                    outboxService.enqueuePasswordReset(user.getEmail(), token);
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 12) {
            throw invalidToken();
        }

        PasswordResetToken resetToken = tokenRepository.findByTokenHashAndUsedAtIsNull(hashToken(token))
                .orElseThrow(this::invalidToken);
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())
                || !"ACTIVE".equalsIgnoreCase(resetToken.getUser().getStatus())) {
            throw invalidToken();
        }

        UserAccount user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAuthVersion((user.getAuthVersion() == null ? 0L : user.getAuthVersion()) + 1L);
        resetToken.setUsedAt(LocalDateTime.now());
        userRepository.save(user);
        tokenRepository.save(resetToken);
    }

    private String createToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to secure password reset", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean validEmail(String email) {
        return email.length() <= 254 && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or expired");
    }
}
