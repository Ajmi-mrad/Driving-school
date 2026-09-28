package com.example.communicationservice.web.dto;

import com.example.communicationservice.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Demande d'émission d'une notification liée à une séance, envoyée par le booking-service via un
 * jeton de service ({@code client_credentials}, rôle {@code SERVICE}). Le booking-service compose le
 * libellé (titre/corps) car il seul connaît le contexte métier ; la communication-service se limite à
 * persister et diffuser.
 *
 * <p>{@code recipientId} est le {@code sub} Keycloak du destinataire ; {@code referenceId} pointe
 * vers la séance concernée (permet au frontend d'ouvrir la séance depuis la notification).
 */
public record CreateBookingNotificationRequest(
        @NotBlank String recipientId,
        @NotNull NotificationType type,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 1000) String body,
        @Size(max = 255) String referenceId
) {
}
