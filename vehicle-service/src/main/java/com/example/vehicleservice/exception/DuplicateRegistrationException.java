package com.example.vehicleservice.exception;

/** Levée lorsqu'un véhicule existe déjà avec la même immatriculation. */
public class DuplicateRegistrationException extends RuntimeException {

    public DuplicateRegistrationException(String registrationNumber) {
        super("Immatriculation déjà utilisée: " + registrationNumber);
    }
}