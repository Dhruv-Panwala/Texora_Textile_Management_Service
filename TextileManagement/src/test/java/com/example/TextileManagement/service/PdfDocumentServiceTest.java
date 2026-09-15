package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.math.BigDecimal;
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
import com.lowagie.text.pdf.parser.PdfTextExtractor;

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
                org.mockito.Mockito.mock(PrivateObjectStorageService.class), 200, 20).generateChallan(sale);

        PdfReader reader = new PdfReader(pdf);
        assertEquals(2, reader.getNumberOfPages());
        reader.close();
    }

    @Test
    void challanWithFortyEightTakasFitsOnOnePage() throws Exception {
        CompanyProfile company = new CompanyProfile();
        company.setTradeName("Test Company Textile Private Limited");
        company.setGstNo("24ABCDE1234F1Z5");
        company.setPhone("+91 95121 51000");
        company.setAddress("101, Textile Market, Ring Road, Surat - 395002");
        Sale sale = saleWithTakas(48);
        sale.getCustomer().setName("Shree Textile Trading Company");
        sale.getCustomer().setAddress("204, New Textile Market, Ring Road, Surat - 395002");
        sale.setBrokerName("Devashish Textile Brokers");
        sale.setQuality("ARTSILK CLOTH - PREMIUM QUALITY");
        when(currentCompanyContext.getCompanyId()).thenReturn(null);
        when(companyProfileRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(company));

        byte[] pdf = new PdfDocumentService(companyProfileRepository, currentCompanyContext,
                org.mockito.Mockito.mock(PrivateObjectStorageService.class), 200, 20).generateChallan(sale);

        PdfReader reader = new PdfReader(pdf);
        assertEquals(1, reader.getNumberOfPages());
        reader.close();
    }

    @Test
    void challanAndBillRenderMetersWithTwoDecimalPlaces() throws Exception {
        CompanyProfile company = new CompanyProfile();
        company.setTradeName("Test Company");
        Sale sale = saleWithTakas(1);
        sale.getTakaEntries().get(0).setMeters(new BigDecimal("0.70"));
        sale.setTotalMeters(new BigDecimal("0.70"));
        when(currentCompanyContext.getCompanyId()).thenReturn(null);
        when(companyProfileRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(company));

        PdfDocumentService service = new PdfDocumentService(companyProfileRepository, currentCompanyContext,
                org.mockito.Mockito.mock(PrivateObjectStorageService.class), 200, 20);

        PdfReader challanReader = new PdfReader(service.generateChallan(sale));
        assertEquals(true, new PdfTextExtractor(challanReader).getTextFromPage(1, false).contains("0.70"));
        challanReader.close();

        PdfReader billReader = new PdfReader(service.generateBill(sale));
        assertEquals(true, new PdfTextExtractor(billReader).getTextFromPage(1, false).contains("0.70"));
        billReader.close();
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
            taka.setMeters(new BigDecimal("10.00"));
            sale.getTakaEntries().add(taka);
        }
        return sale;
    }
}
