package com.example.TextileManagement.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "purchases")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate purchaseDate;

    @ManyToOne(fetch = FetchType.EAGER)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private CompanyProfile company;

    private String materialType;
    private String description;
    private Double quantity;
    @Column(precision = 19, scale = 2)
    private BigDecimal rate;
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    private LocalDate dueDate;
    private LocalDate paymentDate;
    private String paymentMode;
    private String chequeNo;
    private String status;
    @Version
    private Long version;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
