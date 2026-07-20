package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.repository.CompanyProfileRepository;

@SpringBootTest
@ActiveProfiles("dev")
class BusinessNumberSequenceServiceTest {
    @Autowired
    private BusinessNumberSequenceService sequenceService;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Test
    void numbersResetPerFinancialYearAndManualNumbersAdvanceTheSequence() {
        CompanyProfile company = companyProfileRepository.findByTradeNameIgnoreCase("Devashish Textile")
                .orElseThrow();

        BusinessNumberSequenceService.AllocatedNumbers first = sequenceService.reserve(
                company.getId(), "2099-2100", null, null, true, true, 2);
        assertEquals(1, first.challanNumber());
        assertEquals(1, first.billNumber());

        BusinessNumberSequenceService.AllocatedNumbers manual = sequenceService.reserve(
                company.getId(), "2099-2100", 7, 9, false, false, 1);
        assertEquals(7, manual.challanNumber());
        assertEquals(9, manual.billNumber());

        BusinessNumberSequenceService.AllocatedNumbers next = sequenceService.reserve(
                company.getId(), "2099-2100", null, null, true, true, 1);
        assertEquals(8, next.challanNumber());
        assertEquals(10, next.billNumber());

        BusinessNumberSequenceService.AllocatedNumbers newYear = sequenceService.reserve(
                company.getId(), "2100-2101", null, null, true, true, 1);
        assertEquals(1, newYear.challanNumber());
        assertEquals(1, newYear.billNumber());
    }
}
