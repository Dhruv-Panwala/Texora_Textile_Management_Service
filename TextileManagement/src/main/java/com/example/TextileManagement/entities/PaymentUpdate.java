package com.example.TextileManagement.entities;

import java.time.LocalDate;

public record PaymentUpdate(
        LocalDate paymentDate,
        String paymentMode,
        String chequeNo,
        Long version) {
}
