package com.example.authservice.web.dto;

import com.example.authservice.domain.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Représentation exposée d'un utilisateur (jamais l'entité directement).
 */
public record UserResponse(
        UUID id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<String> phones,
        boolean active,
        Set<Role> roles,
        boolean notificationsEnabled,
        String permitNumber,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {
}