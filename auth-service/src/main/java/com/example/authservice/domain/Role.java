package com.example.authservice.domain;

/**
 * Rôles applicatifs, alignés sur les rôles du realm Keycloak {@code auto-ecole}.
 */
public enum Role {
    OWNER,
    SECRETARY,
    MONITOR,
    CLIENT
}