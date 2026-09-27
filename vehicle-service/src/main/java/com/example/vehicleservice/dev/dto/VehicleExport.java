package com.example.vehicleservice.dev.dto;

import com.example.vehicleservice.web.dto.MaintenanceResponse;
import com.example.vehicleservice.web.dto.VehicleResponse;

import java.util.List;

/** Export JSON des données véhicules (parc + entretien). */
public record VehicleExport(List<VehicleResponse> vehicles, List<MaintenanceResponse> maintenance) {
}
