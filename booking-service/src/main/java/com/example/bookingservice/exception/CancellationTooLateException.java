package com.example.bookingservice.exception;

/** Annulation demandée après l'expiration du délai de préavis. → HTTP 422. */
public class CancellationTooLateException extends RuntimeException {
    public CancellationTooLateException(String message) {
        super(message);
    }
}