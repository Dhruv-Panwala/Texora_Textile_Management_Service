package com.example.TextileManagement.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String publicUrl;

    public MailService(JavaMailSender mailSender,
            @Value("${app.mail.from:}") String from,
            @Value("${app.public-url:http://localhost:5173}") String publicUrl) {
        this.mailSender = mailSender;
        this.from = from == null ? "" : from.trim();
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
    }

    public void sendPasswordReset(String recipient, String token) {
        if (from.isBlank() || publicUrl.isBlank()) {
            throw new IllegalStateException("Password reset mail is not configured");
        }
        String resetLink = publicUrl + "/reset-password?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Reset your Texora password");
        message.setText("A password reset was requested for your Texora account.\n\n"
                + "Open this link within 15 minutes to choose a new password:\n"
                + resetLink + "\n\n"
                + "If you did not request this, you can ignore this email.");
        mailSender.send(message);
    }

    public void sendInvitation(String recipient, String workspaceName, String role, String token) {
        if (from.isBlank() || publicUrl.isBlank()) {
            throw new IllegalStateException("Invitation mail is not configured");
        }
        String invitationLink = publicUrl + "/accept-invitation?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Invitation to join " + safeHeaderValue(workspaceName) + " on Texora");
        message.setText("You have been invited to join " + safeBodyValue(workspaceName)
                + " on Texora as a " + safeBodyValue(role) + ".\n\n"
                + "Accept the invitation using this link:\n" + invitationLink + "\n\n"
                + "This link expires in 7 days. If you were not expecting this invitation, you can ignore this email.");
        mailSender.send(message);
    }

    private String safeHeaderValue(String value) {
        return safeBodyValue(value).replaceAll("[\\r\\n]", " ");
    }

    private String safeBodyValue(String value) {
        return value == null ? "" : value.trim();
    }
}
