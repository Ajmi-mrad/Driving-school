package com.example.bookingservice.domain;

/**
 * Cycle de vie d'un examen planifié.
 * <ul>
 *   <li>{@code SCHEDULED} — examen planifié, en attente de résultat.</li>
 *   <li>{@code PASSED} — examen réussi.</li>
 *   <li>{@code FAILED} — examen échoué.</li>
 *   <li>{@code NO_SHOW} — élève absent le jour de l'examen.</li>
 *   <li>{@code CANCELLED} — examen annulé (par le staff) avant sa tenue.</li>
 * </ul>
 */
public enum ExamStatus {
    SCHEDULED,
    PASSED,
    FAILED,
    NO_SHOW,
    CANCELLED
}
