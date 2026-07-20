package com.example.TextileManagement.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.example.TextileManagement.entities.Sale;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    @Override
    @EntityGraph(attributePaths = {"customer", "takaEntries", "company"})
    List<Sale> findAll();

    @Override
    @EntityGraph(attributePaths = {"customer", "takaEntries", "company"})
    Optional<Sale> findById(Long id);

    @EntityGraph(attributePaths = {"customer", "takaEntries", "company"})
    List<Sale> findAllByCompany_Id(Long companyId);

    @EntityGraph(attributePaths = {"customer", "takaEntries", "company"})
    Page<Sale> findAllByCompany_Id(Long companyId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "takaEntries", "company"})
    Optional<Sale> findByIdAndCompany_Id(Long id, Long companyId);

    List<Sale> findByCompany_IdAndCustomer_NameContainingIgnoreCase(Long companyId, String name);

    @Query("""
            select coalesce(sum(s.amount), 0), count(s), coalesce(sum(s.totalMeters), 0)
            from Sale s where s.company.id = :companyId and s.saleDate between :start and :end
            """)
    List<Object[]> aggregateByCompanyAndSaleDateBetween(Long companyId, LocalDate start, LocalDate end);

    @Query("select coalesce(sum(s.amount), 0) from Sale s where s.company.id = :companyId and s.status <> 'PAID'")
    Object outstandingAmount(Long companyId);

    @Query("select count(s) from Sale s where s.company.id = :companyId and s.status <> 'PAID' and s.dueDate < :today")
    long countOverdue(Long companyId, LocalDate today);

    @Query("""
            select s.quality, coalesce(sum(s.amount), 0)
            from Sale s where s.company.id = :companyId and s.saleDate between :start and :end
              and s.quality is not null and trim(s.quality) <> ''
            group by s.quality order by sum(s.amount) desc
            """)
    List<Object[]> topQualities(Long companyId, LocalDate start, LocalDate end);

    @Query("""
            select count(s) > 0 from Sale s
            where s.company.id = :companyId
              and s.financialYear = :financialYear
              and (:excludeId is null or s.id <> :excludeId)
              and s.challanNo <= :rangeEnd
              and ((s.challanNo + s.challanCount) - 1) >= :rangeStart
            """)
    boolean existsOverlappingChallanRange(Long companyId, String financialYear, int rangeStart, int rangeEnd, Long excludeId);

    boolean existsByCompany_IdAndFinancialYearAndBillNo(Long companyId, String financialYear, Integer billNo);

    boolean existsByCompany_IdAndFinancialYearAndBillNoAndIdNot(Long companyId, String financialYear, Integer billNo, Long id);
}
