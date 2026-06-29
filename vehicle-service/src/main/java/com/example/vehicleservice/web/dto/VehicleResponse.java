package com.example.vehicleservice.web.dto;

import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.GearboxType;
import com.example.vehicleservice.domain.VehicleStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Représentation exposée d'un véhicule. */
public record VehicleResponse(
        UUID id,
        String brand,
        String model,
        String registrationNumber,
        GearboxType gearboxType,
        FuelType fuelType,
        VehicleStatus status,
        Integer manufactureYear,
        Integer mileage,
        LocalDate insuranceExpiry,
        LocalDate technicalInspectionExpiry,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {
}