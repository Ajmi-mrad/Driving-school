package com.example.bookingservice.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Déclaration d'une absence ponctuelle d'un moniteur (congé, maladie…). La cohérence
 * {@code endTime > startTime} est vérifiée côté service.
 */
public record CreateTimeOffRequest(
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @Size(max = 255) String reason
) {
}