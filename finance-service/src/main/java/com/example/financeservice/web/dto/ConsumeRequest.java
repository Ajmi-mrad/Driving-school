package com.example.financeservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Décompte de consommation envoyé par le booking-service lorsqu'une séance est réalisée : heures de
 * conduite et/ou séances de code à déduire de l'inscription active du client.
 */
public record ConsumeRequest(
        @NotBlank String clientId,
        @PositiveOrZero int drivingHours,
        @PositiveOrZero int codeSessions
) {
}
