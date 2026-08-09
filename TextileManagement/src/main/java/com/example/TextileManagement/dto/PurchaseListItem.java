package com.example.TextileManagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurchaseListItem(
        Long id,
        LocalDate purchaseDate,
        SupplierSummary supplier,
        String materialType,
        String description,
        Double quantity,
        BigDecimal rate,
        BigDecimal amount,
        LocalDate dueDate,
        LocalDate paymentDate,
        String paymentMode,
        String chequeNo,
        String status,
        LocalDateTime createdAt) {

    public PurchaseListItem(
            Long id,
            LocalDate purchaseDate,
            Long supplierId,
            String supplierName,
            String materialType,
            String description,
            Double quantity,
            BigDecimal rate,
            BigDecimal amount,
            LocalDate dueDate,
            LocalDate paymentDate,
            String paymentMode,
            String chequeNo,
            String status,
            LocalDateTime createdAt) {
        this(id, purchaseDate, new SupplierSummary(supplierId, supplierName), materialType, description,
                quantity, rate, amount, dueDate, paymentDate, paymentMode, chequeNo, status, createdAt);
    }

    public record SupplierSummary(Long id, String name) {
    }
}
