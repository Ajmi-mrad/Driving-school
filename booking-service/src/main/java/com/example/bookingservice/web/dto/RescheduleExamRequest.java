package com.example.bookingservice.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Nouvelle date/heure pour le report d'un examen. */
public record RescheduleExamRequest(
        @NotNull Instant scheduledAt
) {
}
