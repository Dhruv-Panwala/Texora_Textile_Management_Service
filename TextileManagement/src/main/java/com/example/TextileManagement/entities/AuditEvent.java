package com.example.TextileManagement.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_events")
@Data
@NoArgsConstructor
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "actor_username", length = 254)
    private String actorUsername;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 300)
    private String path;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
