package com.example.TextileManagement.controller;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.PaymentUpdate;
import com.example.TextileManagement.entities.Purchase;
import com.example.TextileManagement.entities.Supplier;
import com.example.TextileManagement.dto.PurchaseListItem;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.PurchaseRepository;
import com.example.TextileManagement.repository.SupplierRepository;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final CompanyProfileRepository companyRepository;
    private final CurrentCompanyContext currentCompanyContext;

    public PurchaseController(PurchaseRepository purchaseRepository, SupplierRepository supplierRepository, CompanyProfileRepository companyRepository, CurrentCompanyContext currentCompanyContext) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.companyRepository = companyRepository;
        this.currentCompanyContext = currentCompanyContext;
    }

    @GetMapping
    public PageResponse<PurchaseListItem> getAll(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.from(purchaseRepository.findPageForList(currentCompanyId(),
                PageRequest.of(Math.max(0, page), boundedSize, Sort.by("purchaseDate").descending())));
    }

    @PostMapping
    public ResponseEntity<Purchase> create(@RequestBody Purchase request) {
        request.setId(null);
        request.setCompany(currentCompany());
        return new ResponseEntity<>(purchaseRepository.save(prepare(request)), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Purchase> update(@PathVariable Long id, @RequestBody Purchase request) {
        return purchaseRepository.findByIdAndCompany_Id(id, currentCompanyId()).map(existing -> {
            existing.setPurchaseDate(request.getPurchaseDate());
            existing.setSupplier(resolveSupplier(request.getSupplier()));
            existing.setMaterialType(request.getMaterialType());
            existing.setDescription(request.getDescription());
            existing.setQuantity(request.getQuantity());
            existing.setRate(request.getRate());
            if (request.getPaymentDate() != null) {
                existing.setPaymentDate(request.getPaymentDate());
                existing.setPaymentMode(request.getPaymentMode());
                existing.setChequeNo(request.getChequeNo());
            }
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                existing.setStatus(request.getStatus());
            }
            return ResponseEntity.ok(purchaseRepository.save(prepare(existing)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/paid")
    @Transactional
    public ResponseEntity<Purchase> markPaid(@PathVariable Long id, @RequestBody PaymentUpdate payment) {
        return purchaseRepository.findByIdAndCompany_Id(id, currentCompanyId()).map(purchase -> {
            purchase.setStatus("PAID");
            applyPaymentDetails(purchase, payment);
            return ResponseEntity.ok(purchaseRepository.save(purchase));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!purchaseRepository.existsByIdAndCompany_Id(id, currentCompanyId())) {
            return ResponseEntity.notFound().build();
        }
        purchaseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Purchase prepare(Purchase purchase) {
        Supplier supplier = resolveSupplier(purchase.getSupplier());
        purchase.setSupplier(supplier);
        if (purchase.getPurchaseDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase date is required");
        }
        if (purchase.getMaterialType() == null || purchase.getMaterialType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Material type is required");
        }
        purchase.setMaterialType(purchase.getMaterialType().trim().toUpperCase());
        if ("MISCELLANEOUS".equals(purchase.getMaterialType())
                && (purchase.getDescription() == null || purchase.getDescription().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required for miscellaneous purchases");
        }
        if (purchase.getQuantity() == null || purchase.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than zero");
        }
        if (purchase.getRate() == null || purchase.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate must be greater than zero");
        }
        purchase.setDueDate(purchase.getPurchaseDate().plusDays(45));
        purchase.setRate(purchase.getRate().setScale(2, RoundingMode.HALF_UP));
        purchase.setAmount(BigDecimal.valueOf(purchase.getQuantity())
                .multiply(purchase.getRate())
                .setScale(2, RoundingMode.HALF_UP));
        if (purchase.getStatus() == null || purchase.getStatus().isBlank()) {
            purchase.setStatus("PENDING");
        }
        return purchase;
    }

    private Supplier resolveSupplier(Supplier requestedSupplier) {
        if (requestedSupplier == null || requestedSupplier.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier ID is required");
        }
        return supplierRepository.findByIdAndCompany_Id(requestedSupplier.getId(), currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier not found"));
    }

    private void applyPaymentDetails(Purchase purchase, PaymentUpdate payment) {
        PaymentUpdate details = validatePaymentDetails(payment, purchase.getPurchaseDate());
        purchase.setPaymentDate(details.paymentDate());
        purchase.setPaymentMode(details.paymentMode());
        purchase.setChequeNo(details.chequeNo());
    }

    private PaymentUpdate validatePaymentDetails(PaymentUpdate payment, LocalDate purchaseDate) {
        if (payment == null || payment.paymentDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date is required");
        }
        if (purchaseDate != null && payment.paymentDate().isBefore(purchaseDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date cannot be before the purchase date");
        }
        String mode = payment.paymentMode() == null ? "" : payment.paymentMode().trim().toUpperCase();
        if (!List.of("CASH", "CHEQUE", "UPI").contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment mode must be Cash, Cheque, or UPI");
        }
        String chequeNo = payment.chequeNo() == null ? "" : payment.chequeNo().trim();
        if ("CHEQUE".equals(mode) && chequeNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cheque number is required");
        }
        if (!"CHEQUE".equals(mode)) {
            chequeNo = "";
        }
        return new PaymentUpdate(payment.paymentDate(), mode, chequeNo);
    }

    private Long currentCompanyId() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required");
        }
        return companyId;
    }

    private CompanyProfile currentCompany() {
        return companyRepository.findById(currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company not found"));
    }
}
