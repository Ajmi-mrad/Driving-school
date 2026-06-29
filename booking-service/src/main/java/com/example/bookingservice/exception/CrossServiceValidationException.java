package com.example.bookingservice.exception;

/**
 * Référence invalide vers un autre service (élève/moniteur introuvable, inactif ou de mauvais rôle ;
 * véhicule introuvable ou indisponible). → HTTP 400.
 */
public class CrossServiceValidationException extends RuntimeException {
    public CrossServiceValidationException(String message) {
        super(message);
    }
}