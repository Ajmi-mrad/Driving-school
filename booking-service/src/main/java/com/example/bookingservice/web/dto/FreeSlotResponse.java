package com.example.bookingservice.web.dto;

import java.time.Instant;

/**
 * Créneau réservable calculé pour un moniteur : intervalle absolu {@code [startTime, endTime)} issu des
 * heures de travail, déduction faite des séances réservées et des absences. Consommé par la réservation
 * et par l'assistant de planification par IA.
 */
public record FreeSlotResponse(
        Instant startTime,
        Instant endTime
) {
}