package com.example.bookingservice.exception;

/** Chevauchement de créneau (moniteur, véhicule ou élève déjà réservé). → HTTP 409. */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}