package com.example.vehicleservice.exception;

import java.util.UUID;

/** Levée lorsqu'aucun véhicule ne correspond à l'identifiant demandé. */
public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(UUID id) {
        super("Véhicule introuvable: " + id);
    }
}