package com.example.TextileManagement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.repository.PurchaseRepository;
import com.example.TextileManagement.repository.SaleRepository;

class DashboardControllerTest {
    @Test
    void summaryUsesGroupedQueriesAndReusesTrendTotals() {
        PurchaseRepository purchaseRepository = mock(PurchaseRepository.class);
        SaleRepository saleRepository = mock(SaleRepository.class);
        CurrentCompanyContext companyContext = mock(CurrentCompanyContext.class);
        DashboardController controller = new DashboardController(purchaseRepository, saleRepository, companyContext);
        LocalDate today = LocalDate.now();
        LocalDate previousMonth = today.minusMonths(1);

        when(companyContext.getCompanyId()).thenReturn(3L);
        when(saleRepository.aggregateDailyByCompanyAndSaleDateBetween(eq(3L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        new Object[] { previousMonth, BigDecimal.valueOf(40), 1L, 4.0 },
                        new Object[] { today, BigDecimal.valueOf(100), 2L, 10.0 }));
        when(purchaseRepository.aggregateDailyByCompanyAndPurchaseDateBetween(eq(3L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        new Object[] { previousMonth, BigDecimal.valueOf(10), 1L, 1.0 },
                        new Object[] { today, BigDecimal.valueOf(50), 1L, 2.0 }));
        when(saleRepository.outstandingAndOverdueByCompany(3L, today))
                .thenReturn(List.<Object[]>of(new Object[] { BigDecimal.valueOf(80), 2L }));
        when(purchaseRepository.outstandingAndOverdueByCompany(3L, today))
                .thenReturn(List.<Object[]>of(new Object[] { BigDecimal.valueOf(30), 1L }));
        when(saleRepository.topQualities(eq(3L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.<Object[]>of(new Object[] { "SILK", BigDecimal.valueOf(100) }));

        DashboardController.DashboardSummary summary = controller.summary("monthly");

        assertEquals(BigDecimal.valueOf(100), summary.current().totalSales());
        assertEquals(BigDecimal.valueOf(50), summary.current().totalPurchases());
        assertEquals(BigDecimal.valueOf(40), summary.previous().totalSales());
        assertEquals(BigDecimal.valueOf(10), summary.previous().totalPurchases());
        assertEquals(BigDecimal.valueOf(80), summary.outstandingReceivables());
        assertEquals(2, summary.overdueReceivables());
        assertEquals("SILK", summary.topQuality().quality());
        assertEquals(6, summary.trend().size());
        assertEquals(BigDecimal.valueOf(100), summary.trend().get(5).sales());

        verify(saleRepository).aggregateDailyByCompanyAndSaleDateBetween(eq(3L), any(LocalDate.class), any(LocalDate.class));
        verify(purchaseRepository).aggregateDailyByCompanyAndPurchaseDateBetween(eq(3L), any(LocalDate.class), any(LocalDate.class));
        verify(saleRepository).outstandingAndOverdueByCompany(3L, today);
        verify(purchaseRepository).outstandingAndOverdueByCompany(3L, today);
        verify(saleRepository).topQualities(eq(3L), any(LocalDate.class), any(LocalDate.class));
        verifyNoMoreInteractions(saleRepository, purchaseRepository);
    }
}
