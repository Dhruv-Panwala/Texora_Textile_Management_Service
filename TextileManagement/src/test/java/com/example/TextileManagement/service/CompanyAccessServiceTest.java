package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.TextileManagement.repository.CompanyProfileRepository;

@ExtendWith(MockitoExtension.class)
class CompanyAccessServiceTest {
    @Mock
    private CompanyProfileRepository companyProfileRepository;

    @Test
    void accessRequiresMembershipForTheRequestedCompany() {
        CompanyAccessService service = new CompanyAccessService(companyProfileRepository);
        when(companyProfileRepository.countAccessibleByUsernameAndId("owner", 10L)).thenReturn(1L);
        when(companyProfileRepository.countAccessibleByUsernameAndId("owner", 20L)).thenReturn(0L);

        assertTrue(service.canAccess("owner", 10L));
        assertFalse(service.canAccess("owner", 20L));
    }
}
