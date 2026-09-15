package com.example.TextileManagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.Supplier;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.SupplierRepository;
import com.example.TextileManagement.service.VersionConflict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierRepository repository;
    private final CompanyProfileRepository companyRepository;
    private final CurrentCompanyContext currentCompanyContext;

    public SupplierController(SupplierRepository repository, CompanyProfileRepository companyRepository, CurrentCompanyContext currentCompanyContext) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.currentCompanyContext = currentCompanyContext;
    }

    @GetMapping
    public PageResponse<Supplier> getAll(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(repository.findAllByCompany_Id(currentCompanyId(),
                PageRequest.of(Math.max(0, page), boundedSize(size),
                        Sort.by("name").ascending().and(Sort.by("id").ascending()))));
    }

    @PostMapping
    public ResponseEntity<Supplier> create(@RequestBody Supplier supplier) {
        supplier.setId(null);
        normalizeAndValidate(supplier);
        supplier.setCompany(currentCompany());
        return new ResponseEntity<>(repository.save(supplier), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> update(@PathVariable Long id, @RequestBody Supplier request) {
        return repository.findByIdAndCompany_Id(id, currentCompanyId()).map(supplier -> {
            VersionConflict.requireCurrent(request.getVersion(), supplier.getVersion());
            supplier.setName(request.getName());
            supplier.setContact(request.getContact());
            supplier.setAddress(request.getAddress());
            normalizeAndValidate(supplier);
            return ResponseEntity.ok(repository.save(supplier));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsByIdAndCompany_Id(id, currentCompanyId())) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void normalizeAndValidate(Supplier supplier) {
        supplier.setName(supplier.getName() == null ? "" : supplier.getName().trim());
        if (supplier.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier name is required");
        }

        supplier.setContact(supplier.getContact() == null ? "" : supplier.getContact().trim());
        if (supplier.getContact().isBlank() || !supplier.getContact().matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact is required and must contain numbers only");
        }
    }

    private Long currentCompanyId() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required");
        }
        return companyId;
    }

    private int boundedSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private CompanyProfile currentCompany() {
        return companyRepository.findById(currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company not found"));
    }
}
