package com.example.TextileManagement.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.TextileManagement.config.CurrentCompanyContext;
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
        Long companyId = currentCompanyId();
        List<DateRange> trendRanges = selectedPeriod.trendRanges(today);
        DateRange current = trendRanges.get(trendRanges.size() - 1);
        DateRange oldest = trendRanges.get(0);
        Map<LocalDate, RawTotals> salesByDate = totalsByDate(
                saleRepository.aggregateDailyByCompanyAndSaleDateBetween(companyId, oldest.start(), current.end()));
        Map<LocalDate, RawTotals> purchasesByDate = totalsByDate(
                purchaseRepository.aggregateDailyByCompanyAndPurchaseDateBetween(companyId, oldest.start(), current.end()));
        List<PeriodTotals> trendTotals = trendTotals(trendRanges, salesByDate, purchasesByDate);
        PeriodTotals currentTotals = trendTotals.get(trendTotals.size() - 1);
        PeriodTotals previousTotals = trendTotals.get(trendTotals.size() - 2);
        OutstandingTotals salesOutstanding = outstandingTotals(saleRepository.outstandingAndOverdueByCompany(companyId, today));
        OutstandingTotals purchaseOutstanding = outstandingTotals(purchaseRepository.outstandingAndOverdueByCompany(companyId, today));

        return new DashboardSummary(
                selectedPeriod.name().toLowerCase(Locale.ROOT),
                current.start(),
                current.end(),
                currentTotals,
                previousTotals,
                percentChange(currentTotals.totalSales(), previousTotals.totalSales()),
                percentChange(currentTotals.totalPurchases(), previousTotals.totalPurchases()),
                salesOutstanding.amount(),
                purchaseOutstanding.amount(),
                salesOutstanding.overdueCount(),
                purchaseOutstanding.overdueCount(),
                topQuality(companyId, current),
                trend(trendRanges, trendTotals));
    }

    private List<PeriodTotals> trendTotals(List<DateRange> ranges, Map<LocalDate, RawTotals> salesByDate,
            Map<LocalDate, RawTotals> purchasesByDate) {
        List<PeriodTotals> totals = new ArrayList<>();
        for (DateRange range : ranges) {
            totals.add(totalsForRange(range, salesByDate, purchasesByDate));
        }
        return totals;
    }

    private PeriodTotals totalsForRange(DateRange range, Map<LocalDate, RawTotals> salesByDate,
            Map<LocalDate, RawTotals> purchasesByDate) {
        RawTotals sales = totalsForRange(salesByDate, range);
        RawTotals purchases = totalsForRange(purchasesByDate, range);
        BigDecimal totalSales = sales.amount();
        BigDecimal totalPurchases = purchases.amount();
        long saleCount = sales.count();
        long purchaseCount = purchases.count();
        BigDecimal totalMeters = sales.quantity();
        BigDecimal totalPurchaseQuantity = purchases.quantity();

        return new PeriodTotals(
                totalSales,
                totalPurchases,
                totalSales.subtract(totalPurchases),
                saleCount,
                purchaseCount,
                totalMeters,
                totalPurchaseQuantity,
                totalMeters.signum() == 0 ? BigDecimal.ZERO : totalSales.divide(totalMeters, 2, RoundingMode.HALF_UP),
                saleCount == 0 ? BigDecimal.ZERO : totalSales.divide(BigDecimal.valueOf(saleCount), 2, RoundingMode.HALF_UP),
                purchaseCount == 0 ? BigDecimal.ZERO : totalPurchases.divide(BigDecimal.valueOf(purchaseCount), 2, RoundingMode.HALF_UP));
    }

    private Map<LocalDate, RawTotals> totalsByDate(List<Object[]> rows) {
        Map<LocalDate, RawTotals> totals = new HashMap<>();
        for (Object[] row : rows) {
            totals.put((LocalDate) row[0], new RawTotals(decimal(row[1]), longValue(row[2]), decimal(row[3])));
        }
        return totals;
    }

    private RawTotals totalsForRange(Map<LocalDate, RawTotals> totalsByDate, DateRange range) {
        BigDecimal amount = BigDecimal.ZERO;
        long count = 0;
        BigDecimal quantity = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, RawTotals> entry : totalsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            if (date.isBefore(range.start()) || date.isAfter(range.end())) {
                continue;
            }
            RawTotals totals = entry.getValue();
            amount = amount.add(totals.amount());
            count += totals.count();
            quantity = quantity.add(totals.quantity());
        }
        return new RawTotals(amount, count, quantity);
    }

    private OutstandingTotals outstandingTotals(List<Object[]> results) {
        if (results.isEmpty()) {
            return new OutstandingTotals(BigDecimal.ZERO, 0L);
        }
        Object[] values = results.get(0);
        return new OutstandingTotals(decimal(values[0]), longValue(values[1]));
    }

    private List<TrendPoint> trend(List<DateRange> ranges, List<PeriodTotals> totals) {
        List<TrendPoint> points = new ArrayList<>();
        for (int index = 0; index < ranges.size(); index++) {
            DateRange range = ranges.get(index);
            PeriodTotals periodTotals = totals.get(index);
            points.add(new TrendPoint(labelFor(range), periodTotals.totalSales(), periodTotals.totalPurchases(), periodTotals.net()));
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

    private String labelFor(DateRange range) {
        return switch (range.period()) {
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

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
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
                case WEEKLY -> new DateRange(this, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
                case MONTHLY -> new DateRange(this, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
                case YEARLY -> new DateRange(this, LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));
            };
        }

        DateRange previousRange(DateRange range) {
            return switch (this) {
                case WEEKLY -> new DateRange(this, range.start().minusWeeks(1), range.end().minusWeeks(1));
                case MONTHLY -> {
                    LocalDate start = range.start().minusMonths(1).withDayOfMonth(1);
                    yield new DateRange(this, start, start.withDayOfMonth(start.lengthOfMonth()));
                }
                case YEARLY -> new DateRange(this, range.start().minusYears(1), range.end().minusYears(1));
            };
        }

        List<DateRange> trendRanges(LocalDate today) {
            List<DateRange> ranges = new ArrayList<>();
            DateRange range = currentRange(today);
            for (int index = 0; index < 6; index++) {
                ranges.add(0, range);
                range = previousRange(range);
            }
            return ranges;
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
            BigDecimal totalMeters,
            BigDecimal totalPurchaseQuantity,
            BigDecimal averageSalesRate,
            BigDecimal averageSaleValue,
            BigDecimal averagePurchaseValue) {
    }

    public record TopQuality(String quality, BigDecimal amount) {
    }

    public record TrendPoint(String label, BigDecimal sales, BigDecimal purchases, BigDecimal net) {
    }

    private record RawTotals(BigDecimal amount, long count, BigDecimal quantity) {
    }

    private record OutstandingTotals(BigDecimal amount, long overdueCount) {
    }

    private record DateRange(DashboardPeriod period, LocalDate start, LocalDate end) {
    }
}
