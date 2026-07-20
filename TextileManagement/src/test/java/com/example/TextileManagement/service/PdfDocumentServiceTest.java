package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.Customer;
import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.entities.TakaEntry;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.lowagie.text.pdf.PdfReader;

@ExtendWith(MockitoExtension.class)
class PdfDocumentServiceTest {
    @Mock
    private CompanyProfileRepository companyProfileRepository;

    @Mock
    private CurrentCompanyContext currentCompanyContext;

    @Test
    void challanUsesFourColumnsAndStartsASecondPageAfterFortyEightTakas() throws Exception {
        CompanyProfile company = new CompanyProfile();
        company.setTradeName("Test Company");
        Sale sale = saleWithTakas(49);
        when(currentCompanyContext.getCompanyId()).thenReturn(null);
        when(companyProfileRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(company));

        byte[] pdf = new PdfDocumentService(companyProfileRepository, currentCompanyContext,
                org.mockito.Mockito.mock(PrivateObjectStorageService.class)).generateChallan(sale);

        PdfReader reader = new PdfReader(pdf);
        assertEquals(2, reader.getNumberOfPages());
        reader.close();
    }

    private Sale saleWithTakas(int count) {
        Customer customer = new Customer();
        customer.setName("Customer");
        customer.setAddress("Address");
        Sale sale = new Sale();
        sale.setSaleDate(LocalDate.of(2026, 7, 13));
        sale.setCustomer(customer);
        sale.setChallanNo(1);
        sale.setBillNo(1);
        sale.setBrokerName("Broker");
        sale.setQuality("ARTSILK CLOTH");
        sale.setDueDate(LocalDate.of(2026, 8, 27));
        sale.setTakaEntries(new ArrayList<>());
        for (int i = 1; i <= count; i++) {
            TakaEntry taka = new TakaEntry();
            taka.setTakaNo(i);
            taka.setMeters(10.0);
            sale.getTakaEntries().add(taka);
        }
        return sale;
    }
}
