package com.example.vehicleservice.web.dto;

import com.example.vehicleservice.domain.MaintenanceType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Données d'enregistrement d'une opération d'entretien. */
public record CreateMaintenanceRequest(
        @NotNull MaintenanceType type,
        @NotNull LocalDate performedAt,
        BigDecimal cost,
        String description
) {
}