package com.example.TextileManagement.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.TextileManagement.entities.Payment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Repository
public class PaymentQueryRepository {
    private static final String PENDING_PAYMENT_ROWS = """
            SELECT payment_type, source_id, source_date, due_date, entity_name,
                   material_or_cloth_type, amount, version, payment_date, payment_mode, cheque_no, status
            FROM (
                SELECT 'TO_SUPPLIER' AS payment_type,
                       p.id AS source_id,
                       p.purchase_date AS source_date,
                       p.due_date AS due_date,
                       s.name AS entity_name,
                       CASE
                           WHEN p.material_type = 'MISCELLANEOUS'
                                AND p.description IS NOT NULL
                                AND TRIM(p.description) <> ''
                           THEN p.material_type || ' - ' || p.description
                           ELSE p.material_type
                       END AS material_or_cloth_type,
                       p.amount AS amount,
                       p.version AS version,
                       p.payment_date AS payment_date,
                       p.payment_mode AS payment_mode,
                       p.cheque_no AS cheque_no,
                       p.status AS status
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.company_id = :companyId AND p.status = 'PENDING'

                UNION ALL

                SELECT 'FROM_CUSTOMER',
                       s.id,
                       s.sale_date,
                       s.due_date,
                       c.name,
                       s.quality,
                       s.amount,
                       s.version,
                       s.payment_date,
                       s.payment_mode,
                       s.cheque_no,
                       s.status
                FROM sales s
                JOIN customers c ON c.id = s.customer_id
                WHERE s.company_id = :companyId AND s.status = 'PENDING'
            ) pending_payments
            """;

    private static final String PENDING_PAYMENT_COUNT = """
            SELECT
                (SELECT COUNT(*) FROM purchases p
                 WHERE p.company_id = :companyId AND p.status = 'PENDING')
                +
                (SELECT COUNT(*) FROM sales s
                 WHERE s.company_id = :companyId AND s.status = 'PENDING')
            """;

    private final EntityManager entityManager;

    public PaymentQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<Payment> findPendingByCompanyId(Long companyId, Pageable pageable) {
        Query dataQuery = entityManager.createNativeQuery(PENDING_PAYMENT_ROWS
                + " ORDER BY due_date ASC, payment_type ASC, source_id ASC");
        dataQuery.setParameter("companyId", companyId);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<?> rows = dataQuery.getResultList();
        List<Payment> payments = rows.stream()
                .map(row -> toPayment((Object[]) row))
                .toList();

        Query countQuery = entityManager.createNativeQuery(PENDING_PAYMENT_COUNT);
        countQuery.setParameter("companyId", companyId);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        return new PageImpl<>(payments, pageable, total);
    }

    private Payment toPayment(Object[] row) {
        return new Payment(
                (String) row[0],
                ((Number) row[1]).longValue(),
                toLocalDate(row[2]),
                toLocalDate(row[3]),
                (String) row[4],
                (String) row[5],
                toBigDecimal(row[6]),
                ((Number) row[7]).longValue(),
                toNullableLocalDate(row[8]),
                (String) row[9],
                (String) row[10],
                (String) row[11]);
    }

    private LocalDate toNullableLocalDate(Object value) {
        return value == null ? null : toLocalDate(value);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }
}
