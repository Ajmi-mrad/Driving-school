package com.example.bookingservice.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Remplacement en bloc des heures de travail hebdomadaires d'un moniteur (PUT idempotent). Une liste
 * vide efface toute disponibilité déclarée.
 */
public record UpdateAvailabilityRequest(
        @NotNull @Valid List<AvailabilityRuleRequest> rules
) {
}