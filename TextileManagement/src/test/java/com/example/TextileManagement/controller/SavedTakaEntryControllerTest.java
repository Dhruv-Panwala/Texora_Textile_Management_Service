package com.example.TextileManagement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.SavedTakaEntry;
import com.example.TextileManagement.entities.SavedTakaEntryBatch;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.SavedTakaEntryBatchRepository;
import com.example.TextileManagement.repository.SavedTakaEntryRepository;

@ExtendWith(MockitoExtension.class)
class SavedTakaEntryControllerTest {
    @Mock
    private SavedTakaEntryRepository repository;

    @Mock
    private SavedTakaEntryBatchRepository batchRepository;

    @Mock
    private CompanyProfileRepository companyRepository;

    @Mock
    private CurrentCompanyContext companyContext;

    private SavedTakaEntryController controller;
    private CompanyProfile company;

    @BeforeEach
    void setUp() {
        controller = new SavedTakaEntryController(repository, batchRepository, companyRepository, companyContext);
        company = new CompanyProfile();
    }

    @Test
    void createsEntryForCurrentCompany() {
        when(companyContext.getCompanyId()).thenReturn(10L);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(repository.existsByCompany_IdAndTakaNo(10L, 123)).thenReturn(false);
        when(repository.save(any(SavedTakaEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.createEntry(new SavedTakaEntryController.TakaRequest(123, new BigDecimal("450.5")));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(123, response.getBody().getTakaNo());
        assertEquals(new BigDecimal("450.50"), response.getBody().getMeters());
        assertEquals(company, response.getBody().getCompany());
        verify(repository).save(any(SavedTakaEntry.class));
    }

    @Test
    void rejectsNonPositiveMeters() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.createEntry(new SavedTakaEntryController.TakaRequest(123, BigDecimal.ZERO)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void returnsBoundedSortedPageOfEntries() {
        when(companyContext.getCompanyId()).thenReturn(10L);
        SavedTakaEntry entry = new SavedTakaEntry();
        entry.setTakaNo(123);
        entry.setMeters(new BigDecimal("450.5"));
        when(repository.findAllByCompany_Id(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        var response = controller.getEntries(0, 500);

        assertEquals(1, response.content().size());
        assertEquals(1, response.totalElements());
        verify(repository).findAllByCompany_Id(eq(10L), any(Pageable.class));
    }

    @Test
    void createsAnIdempotentBatch() {
        when(companyContext.getCompanyId()).thenReturn(10L);
        when(batchRepository.findByCompany_IdAndIdempotencyKey(10L, "batch-1")).thenReturn(java.util.Optional.empty());
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(repository.existsByCompany_IdAndTakaNo(10L, 123)).thenReturn(false);
        when(repository.existsByCompany_IdAndTakaNo(10L, 124)).thenReturn(false);
        when(batchRepository.saveAndFlush(any(SavedTakaEntryBatch.class))).thenAnswer(invocation -> {
            SavedTakaEntryBatch batch = invocation.getArgument(0);
            batch.setId(42L);
            return batch;
        });
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.createBatch(" batch-1 ", new SavedTakaEntryController.BatchRequest(List.of(
                new SavedTakaEntryController.TakaRequest(123, new BigDecimal("450.5")),
                new SavedTakaEntryController.TakaRequest(124, new BigDecimal("451")))));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        verify(repository).saveAll(any());
    }
}
