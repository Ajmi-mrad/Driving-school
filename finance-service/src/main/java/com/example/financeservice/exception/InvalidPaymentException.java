package com.example.financeservice.exception;

/**
 * Levée lorsqu'un paiement est invalide au regard de l'état de l'inscription (ex. inscription annulée).
 */
public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}
