package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.TextileManagement.entities.PasswordResetToken;
import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.repository.PasswordResetTokenRepository;
import com.example.TextileManagement.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailOutboxService outboxService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, outboxService);
    }

    @Test
    void resetTokenIsStoredHashedAndCanBeUsedOnce() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setEmail("owner@example.com");
        user.setStatus("ACTIVE");
        AtomicReference<PasswordResetToken> stored = new AtomicReference<>();
        AtomicReference<String> rawToken = new AtomicReference<>();
        when(userRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> {
            PasswordResetToken token = invocation.getArgument(0);
            stored.set(token);
            return token;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            rawToken.set(invocation.getArgument(1));
            return null;
        }).when(outboxService).enqueuePasswordReset(any(), any());

        service.requestReset(" owner@example.com ");

        assertNotEquals(rawToken.get(), stored.get().getTokenHash());
        assertTrue(rawToken.get().length() >= 40);
        when(tokenRepository.findByTokenHashAndUsedAtIsNull(any())).thenReturn(Optional.of(stored.get()));
        when(passwordEncoder.encode("new-password-123")).thenReturn("encoded-password");

        service.resetPassword(rawToken.get(), "new-password-123");

        assertEquals("encoded-password", user.getPasswordHash());
        assertEquals(1L, user.getAuthVersion());
        assertTrue(stored.get().getUsedAt() != null);
        verify(userRepository).save(user);
    }
}
