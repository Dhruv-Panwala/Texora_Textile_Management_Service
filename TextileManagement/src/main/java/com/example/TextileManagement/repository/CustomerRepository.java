package com.example.TextileManagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.TextileManagement.entities.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Page<Customer> findAllByCompany_Id(Long companyId, Pageable pageable);

    java.util.Optional<Customer> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByIdAndCompany_Id(Long id, Long companyId);

    List<Customer> findByCompany_IdAndNameContainingIgnoreCase(Long companyId, String name);

    boolean existsByCompany_IdAndGstNoIgnoreCase(Long companyId, String gstNo);

    boolean existsByCompany_IdAndGstNoIgnoreCaseAndIdNot(Long companyId, String gstNo, Long id);
}
