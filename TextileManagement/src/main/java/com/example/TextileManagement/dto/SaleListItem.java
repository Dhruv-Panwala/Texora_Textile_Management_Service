package com.example.TextileManagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.TextileManagement.entities.Customer;
import com.example.TextileManagement.entities.Sale;

public record SaleListItem(
        Long id,
        LocalDate saleDate,
        CustomerSummary customer,
        String brokerName,
        String quality,
        Integer challanNo,
        Integer challanCount,
        boolean balanceChallanColumnsByMeters,
        Integer billNo,
        String financialYear,
        LocalDate dueDate,
        LocalDate paymentDate,
        String paymentMode,
        String chequeNo,
        String status,
        BigDecimal rate,
        BigDecimal amount,
        Double totalMeters,
        LocalDateTime createdAt) {

    public static SaleListItem from(Sale sale) {
        Customer customer = sale.getCustomer();
        return new SaleListItem(
                sale.getId(),
                sale.getSaleDate(),
                customer == null ? null : new CustomerSummary(customer.getId(), customer.getName(), customer.getBrokerName()),
                sale.getBrokerName(),
                sale.getQuality(),
                sale.getChallanNo(),
                sale.getChallanCount(),
                sale.isBalanceChallanColumnsByMeters(),
                sale.getBillNo(),
                sale.getFinancialYear(),
                sale.getDueDate(),
                sale.getPaymentDate(),
                sale.getPaymentMode(),
                sale.getChequeNo(),
                sale.getStatus(),
                sale.getRate(),
                sale.getAmount(),
                sale.getTotalMeters(),
                sale.getCreatedAt());
    }

    public record CustomerSummary(Long id, String name, String brokerName) {
    }
}
