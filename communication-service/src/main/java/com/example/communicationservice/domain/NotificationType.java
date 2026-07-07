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
 * </ul>
 */
public enum NotificationType {
    NEW_MESSAGE,
    PAYMENT_DUE
}
