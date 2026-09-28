package com.example.bookingservice.exception;

import java.util.UUID;

public class TimeOffNotFoundException extends RuntimeException {
    public TimeOffNotFoundException(UUID id) {
        super("Absence introuvable: " + id);
    }
}