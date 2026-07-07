package com.example.financeservice.exception;

import java.util.UUID;

public class EnrollmentNotFoundException extends RuntimeException {

    public EnrollmentNotFoundException(UUID id) {
        super("Inscription introuvable : " + id);
    }
}
