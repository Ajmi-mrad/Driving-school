package com.example.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Vue partielle d'un véhicule renvoyée par le vehicle-service. Le statut est une chaîne
 * (AVAILABLE, MAINTENANCE, OUT_OF_SERVICE…) ; seul AVAILABLE permet une affectation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VehicleInfo(
        UUID id,
        String status
) {
    public static final String AVAILABLE = "AVAILABLE";

    public boolean isAvailable() {
        return AVAILABLE.equals(status);
    }
}