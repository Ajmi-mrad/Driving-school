package com.example.bookingservice.exception;

import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(UUID id) {
        super("Séance introuvable: " + id);
    }
}