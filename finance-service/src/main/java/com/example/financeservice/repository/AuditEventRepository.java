package com.example.financeservice.repository;

import com.example.financeservice.domain.AuditEvent;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Événements les plus récents, filtrés optionnellement par action et/ou type d'entité
     * ({@code null} = pas de filtre). Limité pour borner la charge de la page d'audit.
     */
    @Query("""
            SELECT e FROM AuditEvent e
            WHERE (:action IS NULL OR e.action = :action)
              AND (:entityType IS NULL OR e.entityType = :entityType)
            ORDER BY e.occurredAt DESC
            """)
    List<AuditEvent> findRecent(@Param("action") String action,
                                @Param("entityType") String entityType,
                                Limit limit);
}
