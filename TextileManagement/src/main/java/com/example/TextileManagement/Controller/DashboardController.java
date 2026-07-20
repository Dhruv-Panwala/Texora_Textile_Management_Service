package com.example.TextileManagement.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.Purchase;
import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.repository.PurchaseRepository;
import com.example.TextileManagement.repository.SaleRepository;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final PurchaseRepository purchaseRepository;
    private final SaleRepository saleRepository;
    private final CurrentCompanyContext currentCompanyContext;

    public DashboardController(PurchaseRepository purchaseRepository, SaleRepository saleRepository, CurrentCompanyContext currentCompanyContext) {
        this.purchaseRepository = purchaseRepository;
        this.saleRepository = saleRepository;
        this.currentCompanyContext = currentCompanyContext;
    }

    @GetMapping
    public DashboardSummary summary(@RequestParam(defaultValue = "monthly") String period) {
        DashboardPeriod selectedPeriod = DashboardPeriod.from(period);
        LocalDate today = LocalDate.now();
        DateRange current = selectedPeriod.currentRange(today);
        DateRange previous = selectedPeriod.previousRange(current);

        Long companyId = currentCompanyId();
        PeriodTotals currentTotals = totalsForRange(companyId, current);
        PeriodTotals previousTotals = totalsForRange(companyId, previous);
        BigDecimal totalOutstandingSales = decimal(saleRepository.outstandingAmount(companyId));
        BigDecimal totalOutstandingPurchases = decimal(purchaseRepository.outstandingAmount(companyId));
        long overdueReceivables = saleRepository.countOverdue(companyId, today);
        long overduePayables = purchaseRepository.countOverdue(companyId, today);

        return new DashboardSummary(
                selectedPeriod.name().toLowerCase(Locale.ROOT),
                current.start(),
                current.end(),
                currentTotals,
                previousTotals,
                percentChange(currentTotals.totalSales(), previousTotals.totalSales()),
                percentChange(currentTotals.totalPurchases(), previousTotals.totalPurchases()),
                totalOutstandingSales,
                totalOutstandingPurchases,
                overdueReceivables,
                overduePayables,
                topQuality(companyId, current),
                trend(companyId, selectedPeriod, today));
    }

    private PeriodTotals totalsForRange(Long companyId, DateRange range) {
        Object[] sales = firstAggregate(saleRepository.aggregateByCompanyAndSaleDateBetween(
                companyId, range.start(), range.end()));
        Object[] purchases = firstAggregate(purchaseRepository.aggregateByCompanyAndPurchaseDateBetween(
                companyId, range.start(), range.end()));
        BigDecimal totalSales = decimal(sales[0]);
        BigDecimal totalPurchases = decimal(purchases[0]);
        long saleCount = ((Number) sales[1]).longValue();
        long purchaseCount = ((Number) purchases[1]).longValue();
        double totalMeters = number(sales[2]);
        double totalPurchaseQuantity = number(purchases[2]);

        return new PeriodTotals(
                totalSales,
                totalPurchases,
                totalSales.subtract(totalPurchases),
                saleCount,
                purchaseCount,
                totalMeters,
                totalPurchaseQuantity,
                totalMeters == 0 ? BigDecimal.ZERO : totalSales.divide(BigDecimal.valueOf(totalMeters), 2, RoundingMode.HALF_UP),
                saleCount == 0 ? BigDecimal.ZERO : totalSales.divide(BigDecimal.valueOf(saleCount), 2, RoundingMode.HALF_UP),
                purchaseCount == 0 ? BigDecimal.ZERO : totalPurchases.divide(BigDecimal.valueOf(purchaseCount), 2, RoundingMode.HALF_UP));
    }

    private List<TrendPoint> trend(Long companyId, DashboardPeriod period, LocalDate today) {
        List<TrendPoint> points = new java.util.ArrayList<>();
        DateRange range = period.currentRange(today);
        for (int i = 5; i >= 0; i--) {
            DateRange bucket = range;
            for (int j = 0; j < i; j++) {
                bucket = period.previousRange(bucket);
            }
            PeriodTotals totals = totalsForRange(companyId, bucket);
            points.add(new TrendPoint(labelFor(period, bucket), totals.totalSales(), totals.totalPurchases(), totals.net()));
        }
        return points;
    }

    private TopQuality topQuality(Long companyId, DateRange range) {
        List<Object[]> results = saleRepository.topQualities(companyId, range.start(), range.end());
        if (results.isEmpty()) {
            return new TopQuality("-", BigDecimal.ZERO);
        }
        return new TopQuality(String.valueOf(results.get(0)[0]), decimal(results.get(0)[1]));
    }

    private String labelFor(DashboardPeriod period, DateRange range) {
        return switch (period) {
            case WEEKLY -> range.start().getDayOfMonth() + " " + range.start().getMonth().name().substring(0, 3);
            case MONTHLY -> range.start().getMonth().name().substring(0, 3) + " " + range.start().getYear();
            case YEARLY -> String.valueOf(range.start().getYear());
        };
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private double number(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }

    private Object[] firstAggregate(List<Object[]> results) {
        return results.isEmpty() ? new Object[] { BigDecimal.ZERO, 0L, 0.0 } : results.get(0);
    }

    private Long currentCompanyId() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalArgumentException("Company context is required");
        }
        return companyId;
    }

    private enum DashboardPeriod {
        WEEKLY,
        MONTHLY,
        YEARLY;

        static DashboardPeriod from(String value) {
            try {
                return DashboardPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return MONTHLY;
            }
        }

        DateRange currentRange(LocalDate today) {
            return switch (this) {
                case WEEKLY -> new DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
                case MONTHLY -> new DateRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
                case YEARLY -> new DateRange(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));
            };
        }

        DateRange previousRange(DateRange range) {
            return switch (this) {
                case WEEKLY -> new DateRange(range.start().minusWeeks(1), range.end().minusWeeks(1));
                case MONTHLY -> {
                    LocalDate start = range.start().minusMonths(1).withDayOfMonth(1);
                    yield new DateRange(start, start.withDayOfMonth(start.lengthOfMonth()));
                }
                case YEARLY -> new DateRange(range.start().minusYears(1), range.end().minusYears(1));
            };
        }
    }

    public record DashboardSummary(
            String period,
            LocalDate periodStart,
            LocalDate periodEnd,
            PeriodTotals current,
            PeriodTotals previous,
            BigDecimal salesChangePercent,
            BigDecimal purchasesChangePercent,
            BigDecimal outstandingReceivables,
            BigDecimal outstandingPayables,
            long overdueReceivables,
            long overduePayables,
            TopQuality topQuality,
            List<TrendPoint> trend) {
    }

    public record PeriodTotals(
            BigDecimal totalSales,
            BigDecimal totalPurchases,
            BigDecimal net,
            long saleCount,
            long purchaseCount,
            double totalMeters,
            double totalPurchaseQuantity,
            BigDecimal averageSalesRate,
            BigDecimal averageSaleValue,
            BigDecimal averagePurchaseValue) {
    }

    public record TopQuality(String quality, BigDecimal amount) {
    }

    public record TrendPoint(String label, BigDecimal sales, BigDecimal purchases, BigDecimal net) {
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
