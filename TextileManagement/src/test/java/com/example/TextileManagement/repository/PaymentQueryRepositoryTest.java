package com.example.TextileManagement.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.Customer;
import com.example.TextileManagement.entities.Payment;
import com.example.TextileManagement.entities.Purchase;
import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.entities.Supplier;
import com.example.TextileManagement.entities.TakaEntry;
import com.example.TextileManagement.entities.Workspace;
import com.example.TextileManagement.service.SaleService;

@SpringBootTest
@ActiveProfiles("dev")
class PaymentQueryRepositoryTest {
    @Autowired
    private PaymentQueryRepository paymentQueryRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private CompanyProfileRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleService saleService;

    @Test
    void returnsOnlyPendingRowsInGlobalDueDateOrderAcrossPages() {
        String suffix = Long.toString(System.nanoTime());
        Workspace workspace = new Workspace();
        workspace.setName("Payment query test " + suffix);
        workspace.setSlug("payment-query-test-" + suffix);
        workspace.setStatus("ACTIVE");
        workspace = workspaceRepository.save(workspace);

        CompanyProfile company = new CompanyProfile();
        company.setTradeName("Payment query test " + suffix);
        company.setWorkspace(workspace);
        company = companyRepository.save(company);
        companyContext.setCompanyId(company.getId());

        try {
            Supplier supplier = new Supplier();
            supplier.setName("Supplier " + suffix);
            supplier.setCompany(company);
            supplier = supplierRepository.save(supplier);

            Customer customer = new Customer();
            customer.setName("Customer " + suffix);
            customer.setCompany(company);
            customer = customerRepository.save(customer);

            Purchase pendingPurchase = pendingPurchase(company, supplier, LocalDate.of(2026, 2, 1));
            pendingPurchase = purchaseRepository.save(pendingPurchase);
            Purchase paidPurchase = pendingPurchase(company, supplier, LocalDate.of(2026, 1, 1));
            paidPurchase.setStatus("PAID");
            purchaseRepository.save(paidPurchase);

            Sale pendingSale = saleService.saveSale(pendingSale(customer, LocalDate.of(2026, 1, 1)));
            Sale paidSale = saleService.saveSale(pendingSale(customer, LocalDate.of(2026, 3, 1)));
            paidSale.setStatus("PAID");
            saleRepository.save(paidSale);

            Page<Payment> firstPage = paymentQueryRepository.findPendingByCompanyId(company.getId(), PageRequest.of(0, 1));
            Page<Payment> secondPage = paymentQueryRepository.findPendingByCompanyId(company.getId(), PageRequest.of(1, 1));

            assertEquals(2, firstPage.getTotalElements());
            assertEquals(1, firstPage.getContent().size());
            assertEquals(1, secondPage.getContent().size());
            assertEquals("FROM_CUSTOMER", firstPage.getContent().get(0).type());
            assertEquals(pendingSale.getId(), firstPage.getContent().get(0).sourceId());
            assertEquals("TO_SUPPLIER", secondPage.getContent().get(0).type());
            assertEquals(pendingPurchase.getId(), secondPage.getContent().get(0).sourceId());
            assertFalse(firstPage.getContent().stream().anyMatch(payment -> "TO_SUPPLIER".equals(payment.type())
                    && payment.sourceId().equals(paidPurchase.getId())));
            assertFalse(secondPage.getContent().stream().anyMatch(payment -> "FROM_CUSTOMER".equals(payment.type())
                    && payment.sourceId().equals(paidSale.getId())));
        } finally {
            companyContext.clear();
        }
    }

    private Purchase pendingPurchase(CompanyProfile company, Supplier supplier, LocalDate date) {
        Purchase purchase = new Purchase();
        purchase.setCompany(company);
        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(date);
        purchase.setDueDate(date.plusDays(45));
        purchase.setMaterialType("BEAM");
        purchase.setQuantity(new BigDecimal("2.00"));
        purchase.setRate(BigDecimal.valueOf(500));
        purchase.setAmount(BigDecimal.valueOf(1000));
        purchase.setStatus("PENDING");
        return purchase;
    }

    private Sale pendingSale(Customer customer, LocalDate date) {
        Sale sale = new Sale();
        sale.setCustomer(customer);
        sale.setSaleDate(date);
        sale.setQuality("ARTSILK");
        sale.setRate(BigDecimal.valueOf(100));
        TakaEntry taka = new TakaEntry();
        taka.setTakaNo(1);
        taka.setMeters(new BigDecimal("10.00"));
        sale.setTakaEntries(new ArrayList<>(java.util.List.of(taka)));
        return sale;
    }
}
