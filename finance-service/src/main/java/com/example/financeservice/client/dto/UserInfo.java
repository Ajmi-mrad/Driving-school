package com.example.financeservice.client.dto;

/**
 * Vue minimale d'un utilisateur exposée par l'auth-service ({@code GET /api/users/by-keycloak/{kid}}),
 * utilisée pour valider l'existence d'un client à l'inscription.
 */
public record UserInfo(
        String keycloakId,
        String firstName,
        String lastName,
        String email
) {
}
