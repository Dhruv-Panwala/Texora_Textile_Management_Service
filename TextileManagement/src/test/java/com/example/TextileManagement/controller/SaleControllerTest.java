package com.example.TextileManagement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.service.PdfDocumentService;
import com.example.TextileManagement.service.SaleService;

class SaleControllerTest {
    @Test
    void challanDownloadReturnsGeneratedPdf() {
        SaleService saleService = mock(SaleService.class);
        PdfDocumentService pdfDocumentService = mock(PdfDocumentService.class);
        SaleController controller = new SaleController(saleService, pdfDocumentService);
        Sale sale = new Sale();
        sale.setId(1L);
        byte[] pdf = new byte[] { 1, 2, 3 };

        when(saleService.getSale(1L)).thenReturn(sale);
        when(pdfDocumentService.generateChallan(sale)).thenReturn(pdf);
        when(pdfDocumentService.buildDownloadName("challan", sale)).thenReturn("challan-10-07-26-2.pdf");

        ResponseEntity<byte[]> response = controller.challanPdf(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("attachment; filename=\"challan-10-07-26-2.pdf\"",
                response.getHeaders().getFirst("Content-Disposition"));
        assertArrayEquals(pdf, response.getBody());
    }

    @Test
    void duplicateBillNumberValidationIsNotConvertedToNotFound() {
        SaleService saleService = mock(SaleService.class);
        PdfDocumentService pdfDocumentService = mock(PdfDocumentService.class);
        SaleController controller = new SaleController(saleService, pdfDocumentService);
        ResponseStatusException validationError = new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Bill number already exists in this financial year");
        when(saleService.updateSale(7L, new Sale())).thenThrow(validationError);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> controller.updateSale(7L, new Sale()));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
    }
}
