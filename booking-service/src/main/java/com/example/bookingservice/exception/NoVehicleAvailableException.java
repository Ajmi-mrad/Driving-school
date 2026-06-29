package com.example.bookingservice.exception;

/** Aucun véhicule disponible pour le créneau demandé. → HTTP 409. */
public class NoVehicleAvailableException extends RuntimeException {
    public NoVehicleAvailableException() {
        super("Aucun véhicule disponible pour ce créneau");
    }
}