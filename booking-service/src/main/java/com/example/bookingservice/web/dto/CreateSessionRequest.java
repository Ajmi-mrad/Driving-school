package com.example.bookingservice.web.dto;

import com.example.bookingservice.domain.SessionType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de réservation. {@code clientId} est ignoré et forcé à l'identité de l'appelant lorsque
 * celui-ci est un CLIENT (un élève ne peut réserver que pour lui-même). {@code monitorId} est requis
 * pour une séance de conduite. {@code vehicleId} est optionnel (affectation automatique si absent) et
 * doit rester nul pour une séance de code.
 */
public record CreateSessionRequest(
        @NotNull SessionType type,
        String clientId,
        String monitorId,
        UUID vehicleId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        String notes
) {
}