package com.example.financeservice.domain;

/**
 * Cycle de vie d'une inscription à un forfait.
 *
 * <ul>
 *   <li>{@code ACTIVE} — inscription en cours (heures consommables, solde éventuellement dû).</li>
 *   <li>{@code COMPLETED} — forfait entièrement payé.</li>
 *   <li>{@code CANCELLED} — inscription annulée.</li>
 * </ul>
 */
public enum EnrollmentStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}
