package com.example.bookingservice.exception;

import java.util.UUID;

public class ExamNotFoundException extends RuntimeException {
    public ExamNotFoundException(UUID id) {
        super("Examen introuvable: " + id);
    }
}
