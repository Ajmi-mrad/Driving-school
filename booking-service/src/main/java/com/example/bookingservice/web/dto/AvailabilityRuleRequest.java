package com.example.bookingservice.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Un créneau de travail hebdomadaire. Les heures sont en horloge locale (Africa/Tunis).
 * La cohérence {@code endTime > startTime} est vérifiée côté service.
 */
public record AvailabilityRuleRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {
}