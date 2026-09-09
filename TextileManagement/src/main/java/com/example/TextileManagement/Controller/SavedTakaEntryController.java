package com.example.TextileManagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.SavedTakaEntry;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.SavedTakaEntryRepository;

@RestController
@RequestMapping("/api/taka-entries")
public class SavedTakaEntryController {
    private final SavedTakaEntryRepository repository;
    private final CompanyProfileRepository companyRepository;
    private final CurrentCompanyContext currentCompanyContext;

    public SavedTakaEntryController(SavedTakaEntryRepository repository,
            CompanyProfileRepository companyRepository, CurrentCompanyContext currentCompanyContext) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.currentCompanyContext = currentCompanyContext;
    }

    @GetMapping
    public List<SavedTakaEntry> getEntries() {
        return repository.findAllByCompany_IdOrderByTakaNoAsc(currentCompanyId());
    }

    @PostMapping
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
        entry.setMeters(request.meters());
        return new ResponseEntity<>(repository.save(entry), HttpStatus.CREATED);
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
        if (request == null || request.takaNo() == null || request.takaNo() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka number must be greater than zero");
        }
        if (request.meters() == null || !Double.isFinite(request.meters()) || request.meters() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka meters must be greater than zero");
        }
    }

    public record TakaRequest(Integer takaNo, Double meters) {
    }
}
