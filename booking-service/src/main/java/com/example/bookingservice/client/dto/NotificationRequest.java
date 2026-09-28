package com.example.bookingservice.client.dto;

/**
 * Corps envoyé au communication-service ({@code POST /api/notifications/booking}) pour émettre une
 * notification in-app liée à une séance. {@code type} est le nom d'un {@code NotificationType}
 * {@code SESSION_*} côté communication-service ; {@code recipientId} le {@code sub} Keycloak du
 * destinataire ; {@code referenceId} l'id de la séance concernée.
 */
public record NotificationRequest(
        String recipientId,
        String type,
        String title,
        String body,
        String referenceId
) {
}