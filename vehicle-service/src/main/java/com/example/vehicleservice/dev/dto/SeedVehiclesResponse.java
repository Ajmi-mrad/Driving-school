package com.example.vehicleservice.dev.dto;

import java.util.Map;

/**
 * Réponse du seed véhicules : la carte {@code vehicleKey -> uuid} permet au booking-service de
 * référencer les véhicules fraîchement créés lors du seed des séances.
 */
public record SeedVehiclesResponse(Map<String, String> vehicles, SeedResult result) {
}
