package com.example.TextileManagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.TextileManagement.entities.SavedTakaEntry;

public interface SavedTakaEntryRepository extends JpaRepository<SavedTakaEntry, Long> {
    Page<SavedTakaEntry> findAllByCompany_Id(Long companyId, Pageable pageable);

    List<SavedTakaEntry> findAllByCompany_IdAndBatch_IdOrderByTakaNoAsc(Long companyId, Long batchId);

    Optional<SavedTakaEntry> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndTakaNo(Long companyId, Integer takaNo);
}
