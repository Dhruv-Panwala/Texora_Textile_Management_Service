package com.example.TextileManagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.TextileManagement.entities.SavedTakaEntry;

public interface SavedTakaEntryRepository extends JpaRepository<SavedTakaEntry, Long> {
    List<SavedTakaEntry> findAllByCompany_IdOrderByTakaNoAsc(Long companyId);

    Optional<SavedTakaEntry> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndTakaNo(Long companyId, Integer takaNo);
}
