package com.example.vehicleservice.web.dto;

import com.example.vehicleservice.domain.MaintenanceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Représentation exposée d'une opération d'entretien. */
public record MaintenanceResponse(
        UUID id,
        UUID vehicleId,
        MaintenanceType type,
        LocalDate performedAt,
        BigDecimal cost,
        String description,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {
}