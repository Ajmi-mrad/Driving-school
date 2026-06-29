package com.example.authservice.web.dto;

import com.example.authservice.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Données de création d'un utilisateur. {@code username} identifie le compte (obligatoire) ;
 * l'email est facultatif (certains clients n'en ont pas). Le mot de passe initial est transmis
 * à Keycloak et n'est jamais stocké côté service. Plusieurs téléphones sont possibles.
 */
public record CreateUserRequest(
        @NotBlank String username,
        @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        Set<String> phones,
        @NotBlank @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères") String password,
        @NotEmpty(message = "Au moins un rôle est requis") Set<Role> roles,
        String permitNumber,
        Boolean notificationsEnabled
) {
}