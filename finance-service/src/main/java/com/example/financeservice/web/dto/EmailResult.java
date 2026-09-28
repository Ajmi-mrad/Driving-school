package com.example.financeservice.web.dto;

/**
 * Résultat d'un envoi groupé de documents par email : nombre de messages envoyés et nombre de
 * documents ignorés (client sans email, ou échec d'envoi — l'opération est « best-effort »).
 */
public record EmailResult(
        int sent,
        int skipped
) {
}
