package com.example.TextileManagement.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "company_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    @ToString.Exclude
    private Workspace workspace;

    @JsonIgnore
    public Workspace getWorkspace() {
        return workspace;
    }

    @Column(nullable = false)
    private String tradeName;

    private String gstNo;
    private String phone;
    private String address;
    private String defaultBroker;
    private String defaultQuality;

    // ponytail: private DB storage keeps this dependency-free; move to private object storage when logo volume grows.
    @JsonIgnore
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "logo_data")
    private byte[] logoData;

    private String logoContentType;
    private Integer logoWidth;
    private Integer logoHeight;
    @JsonIgnore
    private String logoStorageKey;
    @Version
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
