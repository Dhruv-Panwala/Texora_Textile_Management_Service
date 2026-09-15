package com.example.TextileManagement.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email_outbox")
@Data
@NoArgsConstructor
public class EmailOutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String messageType;
    private String recipient;
    private String token;
    private String workspaceName;
    private String role;
    private String status;
    private int attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lockedAt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
        if (status == null) {
            status = "READY";
        }
    }
}
