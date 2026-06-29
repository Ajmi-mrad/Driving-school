package com.example.vehicleservice.web.dto;

import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.GearboxType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Données de création d'un véhicule (le statut initial est AVAILABLE). */
public record CreateVehicleRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String registrationNumber,
        @NotNull GearboxType gearboxType,
        @NotNull FuelType fuelType,
        Integer manufactureYear,
        Integer mileage,
        LocalDate insuranceExpiry,
        LocalDate technicalInspectionExpiry
) {
}