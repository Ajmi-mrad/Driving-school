package com.example.financeservice.exception;

/**
 * Levée lorsqu'une validation auprès d'un autre service échoue (ex. client inexistant côté auth-service).
 */
public class CrossServiceValidationException extends RuntimeException {

    public CrossServiceValidationException(String message) {
        super(message);
    }
}
