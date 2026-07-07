package com.example.bookingservice.client.dto;

/**
 * Corps envoyé au finance-service ({@code POST /api/enrollments/consume}) pour déduire la
 * consommation d'une séance réalisée de l'inscription active du client.
 */
public record ConsumeRequest(
        String clientId,
        int drivingHours,
        int codeSessions
) {
}
