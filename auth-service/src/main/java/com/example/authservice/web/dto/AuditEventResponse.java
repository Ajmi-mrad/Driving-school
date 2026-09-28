package com.example.authservice.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue d'un événement d'audit de compte (append-only) exposée au propriétaire.
 */
public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        String actor,
        String action,
        String entityType,
        String entityId,
        String summary) {
}
