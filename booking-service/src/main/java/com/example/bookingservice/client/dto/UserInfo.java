package com.example.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

/**
 * Vue partielle d'un utilisateur renvoyée par l'auth-service (champs nécessaires à la validation
 * d'une réservation). Les autres champs de {@code UserResponse} sont ignorés.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInfo(
        String keycloakId,
        Set<String> roles,
        boolean active
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}