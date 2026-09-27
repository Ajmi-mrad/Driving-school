package com.example.bookingservice.web.dto;

import com.example.bookingservice.domain.ExamType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de planification d'un examen (réservée au staff). {@code monitorId} et {@code vehicleId}
 * sont facultatifs (accompagnateur/véhicule d'examen, surtout pour la conduite). Le numéro de
 * tentative est calculé côté service.
 */
public record CreateExamRequest(
        @NotNull ExamType type,
        @NotNull String clientId,
        String monitorId,
        UUID vehicleId,
        @NotNull Instant scheduledAt,
        @Size(max = 255) String location
) {
}
