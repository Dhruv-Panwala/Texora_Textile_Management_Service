package com.example.TextileManagement.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.TextileManagement.entities.EmailOutboxMessage;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, Long> {
    List<EmailOutboxMessage> findTop25ByStatusAndNextAttemptAtBeforeOrderByIdAsc(String status,
            LocalDateTime now);

    @Modifying
    @Query("update EmailOutboxMessage message set message.status = 'PROCESSING', message.lockedAt = :now "
            + "where message.id = :id and message.status = 'READY'")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update EmailOutboxMessage message set message.status = 'READY', message.lockedAt = null "
            + "where message.status = 'PROCESSING' and message.lockedAt < :cutoff")
    int releaseStale(@Param("cutoff") LocalDateTime cutoff);
}
