package com.example.TextileManagement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.service.PdfDocumentService;
import com.example.TextileManagement.service.SaleService;
import com.example.TextileManagement.service.PrivateObjectStorageService;

class SaleControllerTest {
    @Test
    void duplicateBillNumberValidationIsNotConvertedToNotFound() {
        SaleService saleService = mock(SaleService.class);
        PdfDocumentService pdfDocumentService = mock(PdfDocumentService.class);
        PrivateObjectStorageService objectStorage = mock(PrivateObjectStorageService.class);
        SaleController controller = new SaleController(saleService, pdfDocumentService, objectStorage);
        ResponseStatusException validationError = new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Bill number already exists in this financial year");
        when(saleService.updateSale(7L, new Sale())).thenThrow(validationError);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> controller.updateSale(7L, new Sale()));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
    }
}
