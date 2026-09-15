package com.example.TextileManagement.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.TextileManagement.entities.EmailOutboxMessage;

@Service
public class EmailOutboxDispatcher {
    private final EmailOutboxService outboxService;
    private final MailService mailService;

    public EmailOutboxDispatcher(EmailOutboxService outboxService, MailService mailService) {
        this.outboxService = outboxService;
        this.mailService = mailService;
    }

    @Scheduled(fixedDelayString = "${app.mail.outbox.poll-ms:5000}")
    public void dispatch() {
        // ponytail: bounded polling keeps mail outside business transactions; add a queue when volume warrants it.
        for (EmailOutboxMessage message : outboxService.claimDue()) {
            try {
                if ("PASSWORD_RESET".equals(message.getMessageType())) {
                    mailService.sendPasswordReset(message.getRecipient(), message.getToken());
                } else if ("INVITATION".equals(message.getMessageType())) {
                    mailService.sendInvitation(message.getRecipient(), message.getWorkspaceName(), message.getRole(),
                            message.getToken());
                }
                outboxService.markSent(message.getId());
            } catch (RuntimeException ignored) {
                outboxService.markFailed(message.getId());
            }
        }
    }
}
