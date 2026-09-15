package com.example.TextileManagement.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.TextileManagement.entities.EmailOutboxMessage;
import com.example.TextileManagement.repository.EmailOutboxRepository;

@Service
public class EmailOutboxService {
    private final EmailOutboxRepository repository;

    public EmailOutboxService(EmailOutboxRepository repository) {
        this.repository = repository;
    }

    public void enqueuePasswordReset(String recipient, String token) {
        enqueue("PASSWORD_RESET", recipient, token, null, null);
    }

    public void enqueueInvitation(String recipient, String workspaceName, String role, String token) {
        enqueue("INVITATION", recipient, token, workspaceName, role);
    }

    @Transactional
    public void enqueue(String type, String recipient, String token, String workspaceName, String role) {
        EmailOutboxMessage message = new EmailOutboxMessage();
        message.setMessageType(type);
        message.setRecipient(recipient);
        message.setToken(token);
        message.setWorkspaceName(workspaceName);
        message.setRole(role);
        repository.save(message);
    }

    @Transactional
    public List<EmailOutboxMessage> claimDue() {
        LocalDateTime now = LocalDateTime.now();
        repository.releaseStale(now.minusMinutes(10));
        List<EmailOutboxMessage> claimed = new ArrayList<>();
        for (EmailOutboxMessage candidate : repository
                .findTop25ByStatusAndNextAttemptAtBeforeOrderByIdAsc("READY", now)) {
            if (repository.claim(candidate.getId(), now) == 1) {
                candidate.setStatus("PROCESSING");
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    @Transactional
    public void markSent(Long id) {
        repository.findById(id).ifPresent(message -> {
            message.setStatus("SENT");
            message.setSentAt(LocalDateTime.now());
            message.setLockedAt(null);
        });
    }

    @Transactional
    public void markFailed(Long id) {
        repository.findById(id).ifPresent(message -> {
            int attempts = message.getAttempts() + 1;
            message.setAttempts(attempts);
            message.setStatus("READY");
            message.setLockedAt(null);
            message.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60, Math.max(1, attempts * 2))));
        });
    }
}
