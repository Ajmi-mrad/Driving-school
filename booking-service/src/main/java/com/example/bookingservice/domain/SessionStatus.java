package com.example.bookingservice.domain;

/**
 * Cycle de vie d'une réservation de séance.
 * <ul>
 *   <li>{@code PENDING} — demande en attente de validation.</li>
 *   <li>{@code CONFIRMED} — séance validée (manuellement ou automatiquement).</li>
 *   <li>{@code REFUSED} — demande refusée par le staff/moniteur.</li>
 *   <li>{@code CANCELLED} — séance annulée (par l'élève ou le staff).</li>
 *   <li>{@code COMPLETED} — séance réalisée.</li>
 * </ul>
 */
public enum SessionStatus {
    PENDING,
    CONFIRMED,
    REFUSED,
    CANCELLED,
    COMPLETED
}
