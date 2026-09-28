package com.example.bookingservice.exception;

/**
 * Le moniteur ne peut pas prendre la séance sur ce créneau : hors de ses horaires de travail déclarés,
 * ou sur une période d'absence. → HTTP 409.
 */
public class MonitorUnavailableException extends RuntimeException {
    public MonitorUnavailableException(String message) {
        super(message);
    }
}