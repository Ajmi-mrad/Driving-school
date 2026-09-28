package com.example.authservice.dev.dto;

import java.util.Map;

/**
 * Réponse du seed auth : la carte {@code seedKey -> keycloakSub} permet aux services aval
 * (finance, booking, communication) de référencer les utilisateurs fraîchement créés.
 */
public record SeedUsersResponse(Map<String, String> users, SeedResult result) {
}
