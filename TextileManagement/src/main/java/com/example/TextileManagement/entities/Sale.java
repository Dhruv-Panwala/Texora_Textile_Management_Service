package com.example.TextileManagement.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "sales")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate saleDate;

    @ManyToOne(fetch = FetchType.EAGER)
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private CompanyProfile company;

    private String brokerName;
    private String quality;
    private Integer challanNo;
    private Integer challanCount;
    private Integer billNo;
    private String financialYear;
    private LocalDate dueDate;
    private LocalDate paymentDate;
    private String paymentMode;
    private String chequeNo;
    private String status;
    @Column(precision = 19, scale = 2)
    private BigDecimal rate;
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    private Double totalMeters;
    @Version
    private Long version;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @ToString.Exclude
    private List<TakaEntry> takaEntries = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
