package com.example.TextileManagement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.SavedTakaEntry;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.SavedTakaEntryRepository;

@ExtendWith(MockitoExtension.class)
class SavedTakaEntryControllerTest {
    @Mock
    private SavedTakaEntryRepository repository;

    @Mock
    private CompanyProfileRepository companyRepository;

    @Mock
    private CurrentCompanyContext companyContext;

    private SavedTakaEntryController controller;
    private CompanyProfile company;

    @BeforeEach
    void setUp() {
        controller = new SavedTakaEntryController(repository, companyRepository, companyContext);
        company = new CompanyProfile();
    }

    @Test
    void createsEntryForCurrentCompany() {
        when(companyContext.getCompanyId()).thenReturn(10L);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(repository.existsByCompany_IdAndTakaNo(10L, 123)).thenReturn(false);
        when(repository.save(any(SavedTakaEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.createEntry(new SavedTakaEntryController.TakaRequest(123, 450.5));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(123, response.getBody().getTakaNo());
        assertEquals(450.5, response.getBody().getMeters());
        assertEquals(company, response.getBody().getCompany());
        verify(repository).save(any(SavedTakaEntry.class));
    }

    @Test
    void rejectsNonPositiveMeters() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.createEntry(new SavedTakaEntryController.TakaRequest(123, 0d)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
