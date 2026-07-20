package com.example.TextileManagement.controller;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.Payment;
import com.example.TextileManagement.repository.PurchaseRepository;
import com.example.TextileManagement.repository.SaleRepository;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PurchaseRepository purchaseRepository;
    private final SaleRepository saleRepository;
    private final CurrentCompanyContext currentCompanyContext;

    public PaymentController(PurchaseRepository purchaseRepository, SaleRepository saleRepository, CurrentCompanyContext currentCompanyContext) {
        this.purchaseRepository = purchaseRepository;
        this.saleRepository = saleRepository;
        this.currentCompanyContext = currentCompanyContext;
    }

    @GetMapping
    public List<Payment> getPendingPayments() {
        Long companyId = currentCompanyId();
        Stream<Payment> purchases = purchaseRepository.findAllByCompany_IdOrderByPurchaseDateDesc(companyId).stream()
                .filter(p -> !"PAID".equalsIgnoreCase(p.getStatus()))
                .map(p -> new Payment("TO_SUPPLIER", p.getId(), p.getPurchaseDate(), p.getDueDate(), p.getSupplier().getName(),
                        purchaseLabel(p.getMaterialType(), p.getDescription()), p.getAmount(),
                        p.getPaymentDate(), p.getPaymentMode(), p.getChequeNo(), p.getStatus()));
        Stream<Payment> sales = saleRepository.findAllByCompany_Id(companyId).stream()
                .filter(s -> !"PAID".equalsIgnoreCase(s.getStatus()))
                .map(s -> new Payment("FROM_CUSTOMER", s.getId(), s.getSaleDate(), s.getDueDate(), s.getCustomer().getName(),
                        s.getQuality(), s.getAmount(), s.getPaymentDate(), s.getPaymentMode(), s.getChequeNo(),
                        s.getStatus()));
        return Stream.concat(purchases, sales)
                .sorted(Comparator.comparing(Payment::dueDate))
                .toList();
    }

    private String purchaseLabel(String materialType, String description) {
        if ("MISCELLANEOUS".equalsIgnoreCase(materialType) && description != null && !description.isBlank()) {
            return materialType + " - " + description;
        }
        return materialType;
    }

    private Long currentCompanyId() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalArgumentException("Company context is required");
        }
        return companyId;
    }
}
