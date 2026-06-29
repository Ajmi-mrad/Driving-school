package com.example.bookingservice.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Nouveau créneau pour un report de séance. */
public record RescheduleRequest(
        @NotNull Instant startTime,
        @NotNull Instant endTime
) {
}