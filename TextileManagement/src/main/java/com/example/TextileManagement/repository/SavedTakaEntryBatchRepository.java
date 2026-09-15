package com.example.TextileManagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.TextileManagement.entities.SavedTakaEntryBatch;

public interface SavedTakaEntryBatchRepository extends JpaRepository<SavedTakaEntryBatch, Long> {
    Optional<SavedTakaEntryBatch> findByCompany_IdAndIdempotencyKey(Long companyId, String idempotencyKey);
}
