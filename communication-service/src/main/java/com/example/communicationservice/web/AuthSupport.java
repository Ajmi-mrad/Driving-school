package com.example.communicationservice.web;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extrait le contexte appelant du JWT : l'identifiant Keycloak ({@code sub}) et les rôles « nus »
 * (sans le préfixe {@code ROLE_} ajouté par le converter), pour les passer au service métier.
 */
public final class AuthSupport {

    private static final String ROLE_PREFIX = "ROLE_";

    private AuthSupport() {
    }

    public static String sub(JwtAuthenticationToken authentication) {
        return authentication.getToken().getSubject();
    }

    public static Set<String> roles(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
    }
}