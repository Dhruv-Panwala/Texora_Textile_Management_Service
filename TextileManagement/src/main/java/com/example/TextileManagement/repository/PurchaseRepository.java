package com.example.TextileManagement.repository;

import java.util.List;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import com.example.TextileManagement.entities.Purchase;
import com.example.TextileManagement.dto.PurchaseListItem;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    Page<Purchase> findAllByCompany_Id(Long companyId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(value = """
            select new com.example.TextileManagement.dto.PurchaseListItem(
                p.id, p.purchaseDate, p.supplier.id, p.supplier.name, p.materialType, p.description,
                p.quantity, p.rate, p.amount, p.dueDate, p.paymentDate, p.paymentMode, p.chequeNo,
                p.status, p.version, p.createdAt)
            from Purchase p
            where p.company.id = :companyId
            """, countQuery = "select count(p) from Purchase p where p.company.id = :companyId")
    Page<PurchaseListItem> findPageForList(@org.springframework.data.repository.query.Param("companyId") Long companyId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "company"})
    java.util.Optional<Purchase> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByIdAndCompany_Id(Long id, Long companyId);

    List<Purchase> findByCompany_IdAndSupplier_NameContainingIgnoreCase(Long companyId, String name);

    @org.springframework.data.jpa.repository.Query("""
            select p.purchaseDate, coalesce(sum(p.amount), 0), count(p), coalesce(sum(p.quantity), 0)
            from Purchase p where p.company.id = :companyId and p.purchaseDate between :start and :end
            group by p.purchaseDate
            """)
    List<Object[]> aggregateDailyByCompanyAndPurchaseDateBetween(Long companyId, LocalDate start, LocalDate end);

    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(p.amount), 0), coalesce(sum(case when p.dueDate < :today then 1 else 0 end), 0)
            from Purchase p where p.company.id = :companyId and p.status <> 'PAID'
            """)
    List<Object[]> outstandingAndOverdueByCompany(Long companyId, LocalDate today);
}
