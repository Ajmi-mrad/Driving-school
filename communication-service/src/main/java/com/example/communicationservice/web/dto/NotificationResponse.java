package com.example.communicationservice.web.dto;

import com.example.communicationservice.domain.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Représentation exposée d'une notification. {@code referenceId} (p. ex. conversationId) et
 * {@code amount} (montant dû) ne sont renseignés que selon le {@link NotificationType}.
 */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String referenceId,
        BigDecimal amount,
        Instant readAt,
        Instant createdAt
) {
}
