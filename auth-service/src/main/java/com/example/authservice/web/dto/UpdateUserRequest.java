package com.example.authservice.web.dto;

import jakarta.validation.constraints.Email;

import java.util.Set;

/**
 * Mise à jour de profil. Les champs nuls sont ignorés. Les attributs d'identité (email, prénom,
 * nom) sont propagés vers Keycloak ; les attributs métier restent locaux. Le username n'est pas
 * modifiable.
 */
public record UpdateUserRequest(
        @Email String email,
        String firstName,
        String lastName,
        Set<String> phones,
        String permitNumber,
        Boolean notificationsEnabled
) {
}