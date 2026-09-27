package com.example.bookingservice.dev.dto;

import java.util.Map;

/**
 * Corps du seed booking : cartes {@code seedKey -> keycloakSub} (utilisateurs) et
 * {@code vehicleKey -> uuid} (véhicules), produites par les seeds auth et vehicle, pour renseigner
 * les acteurs et le véhicule de chaque séance.
 */
public record BookingSeedRequest(Map<String, String> users, Map<String, String> vehicles) {
}
