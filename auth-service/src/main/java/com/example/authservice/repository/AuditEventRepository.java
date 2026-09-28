package com.example.authservice.repository;

import com.example.authservice.domain.AuditEvent;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

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
