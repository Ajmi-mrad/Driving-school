package com.example.communicationservice.domain;

/**
 * Nature d'une notification in-app.
 *
 * <ul>
 *   <li>{@link #NEW_MESSAGE} : un message de chat est arrivé alors que le destinataire était
 *       hors-ligne (créée automatiquement par {@code ConversationService}).</li>
 *   <li>{@link #PAYMENT_DUE} : rappel de paiement envoyé <b>manuellement</b> par un administrateur
 *       (propriétaire/secrétaire). Solution d'attente : ne suit ni le paiement ni l'historique —
 *       cela relèvera d'un futur service de facturation.</li>
 *   <li>{@link #SESSION_REQUESTED} : une demande de séance (conduite/code) est en attente de
 *       validation ; adressée au moniteur concerné et au staff.</li>
 *   <li>{@link #SESSION_CONFIRMED} : la demande de l'élève a été validée (ou auto-validée).</li>
 *   <li>{@link #SESSION_REFUSED} : la demande de l'élève a été refusée (motif éventuel dans le corps).</li>
 *   <li>{@link #SESSION_RESCHEDULED} : une séance de l'élève a été reportée par le staff.</li>
 *   <li>{@link #SESSION_CANCELLED} : une séance de l'élève a été annulée par le staff.</li>
 *   <li>{@link #EXAM_SCHEDULED} : un examen (code/conduite) a été planifié pour l'élève.</li>
 *   <li>{@link #EXAM_PASSED} : l'élève a réussi son examen.</li>
 *   <li>{@link #EXAM_FAILED} : l'élève a échoué à son examen (ou y était absent).</li>
 *   <li>{@link #EXAM_RESCHEDULED} : l'examen de l'élève a été reporté par le staff.</li>
 *   <li>{@link #EXAM_CANCELLED} : l'examen de l'élève a été annulé par le staff.</li>
 * </ul>
 *
 * <p>Les types {@code SESSION_*} et {@code EXAM_*} sont émis par le booking-service via l'endpoint
 * interne {@code POST /api/notifications/booking} (jeton {@code SERVICE}).
 */
public enum NotificationType {
    NEW_MESSAGE,
    PAYMENT_DUE,
    SESSION_REQUESTED,
    SESSION_CONFIRMED,
    SESSION_REFUSED,
    SESSION_RESCHEDULED,
    SESSION_CANCELLED,
    EXAM_SCHEDULED,
    EXAM_PASSED,
    EXAM_FAILED,
    EXAM_RESCHEDULED,
    EXAM_CANCELLED
}
