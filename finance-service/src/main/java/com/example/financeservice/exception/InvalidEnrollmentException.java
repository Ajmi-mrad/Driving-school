package com.example.financeservice.exception;

/** Règle métier violée sur une inscription (p. ex. nouveau total inférieur au montant déjà payé). */
public class InvalidEnrollmentException extends RuntimeException {

    public InvalidEnrollmentException(String message) {
        super(message);
    }
}
