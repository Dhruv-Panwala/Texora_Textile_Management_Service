package com.example.TextileManagement.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BusinessNumberSequenceService {
    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public BusinessNumberSequenceService(JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.url}") String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.postgres = datasourceUrl != null && datasourceUrl.startsWith("jdbc:postgresql:");
    }

    @Transactional
    public AllocatedNumbers reserve(Long companyId, String financialYear, Integer requestedChallan,
            Integer requestedBill, boolean allocateChallan, boolean allocateBill, int challanCount) {
        ensureRow(companyId, financialYear);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT next_challan_number, next_bill_number FROM business_number_sequences "
                        + "WHERE company_id = ? AND financial_year = ? FOR UPDATE",
                companyId, financialYear);

        int nextChallan = ((Number) row.get("next_challan_number")).intValue();
        int nextBill = ((Number) row.get("next_bill_number")).intValue();
        int challan = allocateChallan ? nextChallan : requirePositive(requestedChallan, "Challan number");
        int bill = allocateBill ? nextBill : requirePositive(requestedBill, "Bill number");
        int challanSpan = Math.max(1, challanCount);
        if (challan > Integer.MAX_VALUE - challanSpan) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challan number is too large");
        }
        if (bill == Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bill number is too large");
        }
        int challanEndExclusive = challan + challanSpan;
        int nextChallanAfterSale = Math.max(nextChallan, challanEndExclusive);
        int nextBillAfterSale = Math.max(nextBill, bill + 1);

        jdbcTemplate.update(
                "UPDATE business_number_sequences SET next_challan_number = ?, next_bill_number = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE company_id = ? AND financial_year = ?",
                nextChallanAfterSale, nextBillAfterSale, companyId, financialYear);
        return new AllocatedNumbers(challan, bill);
    }

    private void ensureRow(Long companyId, String financialYear) {
        if (postgres) {
            jdbcTemplate.update(
                    "INSERT INTO business_number_sequences "
                            + "(company_id, financial_year, next_challan_number, next_bill_number) "
                            + "VALUES (?, ?, 1, 1) ON CONFLICT (company_id, financial_year) DO NOTHING",
                    companyId, financialYear);
            return;
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO business_number_sequences "
                            + "(company_id, financial_year, next_challan_number, next_bill_number) "
                            + "SELECT ?, ?, 1, 1 WHERE NOT EXISTS ("
                            + "SELECT 1 FROM business_number_sequences WHERE company_id = ? AND financial_year = ?)",
                    companyId, financialYear, companyId, financialYear);
        } catch (DuplicateKeyException ignored) {
            // ponytail: H2-only fallback; PostgreSQL uses ON CONFLICT for atomic first-row creation.
        }
    }

    private int requirePositive(Integer value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return value;
    }

    public record AllocatedNumbers(int challanNumber, int billNumber) {
    }
}
