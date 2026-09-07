package com.example.financeservice.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Représentation exposée d'un événement d'audit (journal consultable par le propriétaire). */
public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        String actor,
        String action,
        String entityType,
        String entityId,
        String summary
) {
}
