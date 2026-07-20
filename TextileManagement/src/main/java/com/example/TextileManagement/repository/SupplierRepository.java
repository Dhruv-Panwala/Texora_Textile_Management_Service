package com.example.TextileManagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.TextileManagement.entities.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findAllByCompany_IdOrderByNameAsc(Long companyId);

    Page<Supplier> findAllByCompany_Id(Long companyId, Pageable pageable);

    java.util.Optional<Supplier> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByIdAndCompany_Id(Long id, Long companyId);

    List<Supplier> findByCompany_IdAndNameContainingIgnoreCase(Long companyId, String name);
}
