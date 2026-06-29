package com.example.vehicleservice.web.dto;

import com.example.vehicleservice.domain.VehicleStatus;
import jakarta.validation.constraints.NotNull;

/** Changement d'état de disponibilité d'un véhicule. */
public record UpdateStatusRequest(
        @NotNull VehicleStatus status
) {
}