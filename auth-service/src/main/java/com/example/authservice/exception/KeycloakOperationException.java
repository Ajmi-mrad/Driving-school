package com.example.authservice.exception;

/**
 * Levée lorsqu'une opération sur l'API Admin Keycloak échoue (création, affectation de rôle, etc.).
 */
public class KeycloakOperationException extends RuntimeException {

    public KeycloakOperationException(String message) {
        super(message);
    }

    public KeycloakOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}