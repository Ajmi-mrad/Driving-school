package com.example.communicationservice.repository;

import com.example.communicationservice.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Fil de notifications d'un utilisateur, plus récentes d'abord. */
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    /** Nombre de notifications non lues d'un utilisateur (badge / cloche). */
    long countByRecipientIdAndReadAtIsNull(String recipientId);

    /** Notification appartenant à un utilisateur donné — garantit qu'on n'agit que sur les siennes. */
    Optional<Notification> findByIdAndRecipientId(UUID id, String recipientId);

    /** Marque comme lues toutes les notifications non lues d'un utilisateur. */
    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.recipientId = :recipientId
              and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") String recipientId, @Param("now") Instant now);
}
