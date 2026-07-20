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
import com.example.TextileManagement.entities.Customer;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerRepository repository;
    private final CompanyProfileRepository companyRepository;
    private final CurrentCompanyContext currentCompanyContext;

    public CustomerController(CustomerRepository repository, CompanyProfileRepository companyRepository, CurrentCompanyContext currentCompanyContext) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.currentCompanyContext = currentCompanyContext;
    }

    @GetMapping
    public PageResponse<Customer> getAllCustomers(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(repository.findAllByCompany_Id(currentCompanyId(),
                PageRequest.of(Math.max(0, page), boundedSize(size), Sort.by("name").ascending())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        return repository.findByIdAndCompany_Id(id, currentCompanyId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
        customer.setId(null);
        normalizeAndValidate(customer, null);
        customer.setCompany(currentCompany());
        return new ResponseEntity<>(repository.save(customer), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id, @RequestBody Customer request) {
        return repository.findByIdAndCompany_Id(id, currentCompanyId()).map(customer -> {
            customer.setName(request.getName());
            customer.setContact(request.getContact());
            customer.setAddress(request.getAddress());
            customer.setGstNo(request.getGstNo());
            customer.setBrokerName(request.getBrokerName());
            customer.setDeliveryAddress(request.getDeliveryAddress());
            normalizeAndValidate(customer, id);
            return ResponseEntity.ok(repository.save(customer));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        if (!repository.existsByIdAndCompany_Id(id, currentCompanyId())) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void normalizeAndValidate(Customer customer, Long existingId) {
        customer.setName(customer.getName() == null ? "" : customer.getName().trim());
        if (customer.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer name is required");
        }

        customer.setContact(customer.getContact() == null ? "" : customer.getContact().trim());
        if (customer.getContact().isBlank() || !customer.getContact().matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact is required and must contain numbers only");
        }

        String gstNo = customer.getGstNo() == null ? "" : customer.getGstNo().trim().toUpperCase();
        if (gstNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GST No is required");
        }
        if (!gstNo.matches("[A-Z0-9]{15}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GST No must be exactly 15 alphanumeric characters");
        }
        boolean duplicate = existingId == null
                ? repository.existsByCompany_IdAndGstNoIgnoreCase(currentCompanyId(), gstNo)
                : repository.existsByCompany_IdAndGstNoIgnoreCaseAndIdNot(currentCompanyId(), gstNo, existingId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GST No must be unique");
        }
        customer.setGstNo(gstNo);
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
