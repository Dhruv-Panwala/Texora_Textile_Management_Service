package com.example.TextileManagement.entities;

import java.time.LocalDate;
import java.math.BigDecimal;

public record Payment(
        String type,
        Long sourceId,
        LocalDate sourceDate,
        LocalDate dueDate,
        String entityName,
        String materialOrClothType,
        BigDecimal amount,
        LocalDate paymentDate,
        String paymentMode,
        String chequeNo,
        String status) {
}
