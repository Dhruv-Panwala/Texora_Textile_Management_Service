package com.example.TextileManagement.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.TextileManagement.entities.PaymentUpdate;
import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.dto.SaleListItem;
import com.example.TextileManagement.service.PdfDocumentService;
import com.example.TextileManagement.service.SaleService;

@RestController
@RequestMapping("/api/sales")
public class SaleController {
    private final SaleService saleService;
    private final PdfDocumentService pdfDocumentService;

    public SaleController(SaleService saleService, PdfDocumentService pdfDocumentService) {
        this.saleService = saleService;
        this.pdfDocumentService = pdfDocumentService;
    }

    @PostMapping
    public ResponseEntity<Sale> createSale(@RequestBody Sale sale) {
        return new ResponseEntity<>(saleService.saveSale(sale), HttpStatus.CREATED);
    }

    @GetMapping
    public PageResponse<SaleListItem> getAllSales(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(saleService.getSalesPage(page, size));
    }

    @GetMapping("/{id}")
    public Sale getSale(@PathVariable Long id) {
        return saleService.getSale(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSale(@PathVariable Long id) {
        return saleService.deleteSaleById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sale> updateSale(@PathVariable Long id, @RequestBody Sale sale) {
        return ResponseEntity.ok(saleService.updateSale(id, sale));
    }

    @PatchMapping("/{id}/paid")
    public Sale markPaid(@PathVariable Long id, @RequestBody PaymentUpdate payment) {
        return saleService.markPaid(id, payment);
    }

    @GetMapping("/{id}/challan.pdf")
    public ResponseEntity<byte[]> challanPdf(@PathVariable Long id) {
        Sale sale = saleService.getSale(id);
        return pdfResponse(pdfDocumentService.generateChallan(sale), "challan", sale);
    }

    @GetMapping("/{id}/bill.pdf")
    public ResponseEntity<byte[]> billPdf(@PathVariable Long id) {
        Sale sale = saleService.ensureBillNo(id);
        return pdfResponse(pdfDocumentService.generateBill(sale), "bill", sale);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] bytes, String type, Sale sale) {
        String filename = pdfDocumentService.buildDownloadName(type, sale);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
