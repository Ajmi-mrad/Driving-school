package com.example.vehicleservice.web.dto;

import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.GearboxType;

import java.time.LocalDate;

/** Mise à jour partielle d'un véhicule : les champs nuls sont ignorés. L'immatriculation
 * et le statut ne sont pas modifiables ici (statut via l'endpoint dédié). */
public record UpdateVehicleRequest(
        String brand,
        String model,
        GearboxType gearboxType,
        FuelType fuelType,
        Integer manufactureYear,
        Integer mileage,
        LocalDate insuranceExpiry,
        LocalDate technicalInspectionExpiry
) {
}