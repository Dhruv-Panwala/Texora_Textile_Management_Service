package com.example.TextileManagement.repository;

import java.util.List;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.TextileManagement.entities.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findAllByCompany_IdOrderByPurchaseDateDesc(Long companyId);

    Page<Purchase> findAllByCompany_Id(Long companyId, Pageable pageable);

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
