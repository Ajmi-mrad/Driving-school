package com.example.authservice.web.dto;

import com.example.authservice.domain.Role;

import java.util.Set;

/**
 * Vue minimale d'un interlocuteur de messagerie (moniteur ou élève), exposée aux participants du
 * chat (moniteurs / élèves). Volontairement sans email/téléphone : seul le nécessaire pour ouvrir une
 * conversation et afficher un nom. L'identifiant est le {@code sub} Keycloak (référence cross-service).
 */
public record ContactResponse(
        String id,
        String firstName,
        String lastName,
        Set<Role> roles
) {
}
