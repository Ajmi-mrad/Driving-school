package com.example.bookingservice.exception;

/** Transition d'état invalide (ex. confirmer une séance qui n'est pas en attente). → HTTP 409. */
public class InvalidSessionStateException extends RuntimeException {
    public InvalidSessionStateException(String message) {
        super(message);
    }
}