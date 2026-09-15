package com.example.TextileManagement.controller;

import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.SavedTakaEntry;
import com.example.TextileManagement.entities.SavedTakaEntryBatch;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.SavedTakaEntryBatchRepository;
import com.example.TextileManagement.repository.SavedTakaEntryRepository;
import com.example.TextileManagement.service.TakaEntryValidator;

@RestController
@RequestMapping("/api/taka-entries")
public class SavedTakaEntryController {
    private final SavedTakaEntryRepository repository;
    private final SavedTakaEntryBatchRepository batchRepository;
    private final CompanyProfileRepository companyRepository;
    private final CurrentCompanyContext currentCompanyContext;

    public SavedTakaEntryController(SavedTakaEntryRepository repository,
            SavedTakaEntryBatchRepository batchRepository, CompanyProfileRepository companyRepository,
            CurrentCompanyContext currentCompanyContext) {
        this.repository = repository;
        this.batchRepository = batchRepository;
        this.companyRepository = companyRepository;
        this.currentCompanyContext = currentCompanyContext;
    }

    @GetMapping
    public PageResponse<SavedTakaEntry> getEntries(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        Sort ordering = Sort.by("takaNo").ascending().and(Sort.by("id").ascending());
        return PageResponse.from(repository.findAllByCompany_Id(currentCompanyId(),
                PageRequest.of(Math.max(0, page), boundedSize, ordering)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<SavedTakaEntry> createEntry(@RequestBody TakaRequest request) {
        validate(request);
        Long companyId = currentCompanyId();
        if (repository.existsByCompany_IdAndTakaNo(companyId, request.takaNo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That taka number is already saved");
        }
        CompanyProfile company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        SavedTakaEntry entry = new SavedTakaEntry();
        entry.setCompany(company);
        entry.setTakaNo(request.takaNo());
        entry.setMeters(TakaEntryValidator.normalizeMeters(request.meters()));
        return new ResponseEntity<>(repository.save(entry), HttpStatus.CREATED);
    }

    @PostMapping("/batch")
    @Transactional
    public ResponseEntity<List<SavedTakaEntry>> createBatch(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody BatchRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key is required");
        }
        Long companyId = currentCompanyId();
        String key = idempotencyKey.trim();
        var existingBatch = batchRepository.findByCompany_IdAndIdempotencyKey(companyId, key);
        if (existingBatch.isPresent()) {
            return ResponseEntity.ok(repository.findAllByCompany_IdAndBatch_IdOrderByTakaNoAsc(companyId, existingBatch.get().getId()));
        }
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka entries are required");
        }
        TakaEntryValidator.validateCount(request.entries().size(), TakaEntryValidator.MAX_TAKA_ENTRIES);
        List<Integer> takaNumbers = new ArrayList<>();
        for (TakaRequest entry : request.entries()) {
            validate(entry);
            takaNumbers.add(entry.takaNo());
        }
        TakaEntryValidator.validateUniqueNumbers(takaNumbers);
        for (Integer takaNo : takaNumbers) {
            if (repository.existsByCompany_IdAndTakaNo(companyId, takaNo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That taka number is already saved: " + takaNo);
            }
        }

        CompanyProfile company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        SavedTakaEntryBatch batch = new SavedTakaEntryBatch();
        batch.setCompany(company);
        batch.setIdempotencyKey(key);
        SavedTakaEntryBatch savedBatch = batchRepository.saveAndFlush(batch);
        List<SavedTakaEntry> entries = request.entries().stream().map(requestEntry -> {
            SavedTakaEntry entry = new SavedTakaEntry();
            entry.setCompany(company);
            entry.setBatch(savedBatch);
            entry.setTakaNo(requestEntry.takaNo());
            entry.setMeters(TakaEntryValidator.normalizeMeters(requestEntry.meters()));
            return entry;
        }).toList();
        repository.saveAll(entries);
        return new ResponseEntity<>(entries, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id) {
        SavedTakaEntry entry = repository.findByIdAndCompany_Id(id, currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Taka entry not found"));
        repository.delete(entry);
        return ResponseEntity.noContent().build();
    }

    private Long currentCompanyId() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required");
        }
        return companyId;
    }

    private void validate(TakaRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka entry is required");
        }
        TakaEntryValidator.validateEntry(request.takaNo(), request.meters());
    }

    public record TakaRequest(Integer takaNo, BigDecimal meters) {
    }

    public record BatchRequest(List<TakaRequest> entries) {
    }
}
