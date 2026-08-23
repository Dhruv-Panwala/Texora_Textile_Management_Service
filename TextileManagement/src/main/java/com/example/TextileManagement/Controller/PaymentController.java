package com.example.TextileManagement.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.Payment;
import com.example.TextileManagement.repository.PaymentQueryRepository;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentQueryRepository paymentQueryRepository;
    private final CurrentCompanyContext currentCompanyContext;

    public PaymentController(PaymentQueryRepository paymentQueryRepository, CurrentCompanyContext currentCompanyContext) {
        this.paymentQueryRepository = paymentQueryRepository;
        this.currentCompanyContext = currentCompanyContext;
    }

    @GetMapping
    public PageResponse<Payment> getPendingPayments(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.from(paymentQueryRepository.findPendingByCompanyId(currentCompanyId(),
                PageRequest.of(Math.max(0, page), boundedSize)));
    }

    private Long currentCompanyId() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalArgumentException("Company context is required");
        }
        return companyId;
    }
}
