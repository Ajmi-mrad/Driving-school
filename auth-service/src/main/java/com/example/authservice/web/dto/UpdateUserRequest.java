package com.example.authservice.web.dto;

import com.example.authservice.domain.Role;
import jakarta.validation.constraints.Email;

import java.util.Set;

/**
 * Mise à jour de profil. Les champs nuls sont ignorés. Les attributs d'identité (email, prénom,
 * nom) sont propagés vers Keycloak ; les attributs métier restent locaux. Le username n'est pas
 * modifiable.
 *
 * <p>{@code roles} (si non nul) remplace l'ensemble des rôles : les rôles realm Keycloak sont
 * synchronisés (ajout/retrait). {@code active} (si non nul) active/désactive le compte — c'est
 * ainsi qu'un utilisateur désactivé peut être réactivé.</p>
 */
public record UpdateUserRequest(
        @Email String email,
        String firstName,
        String lastName,
        Set<String> phones,
        String permitNumber,
        Boolean notificationsEnabled,
        Set<Role> roles,
        Boolean active
) {
}