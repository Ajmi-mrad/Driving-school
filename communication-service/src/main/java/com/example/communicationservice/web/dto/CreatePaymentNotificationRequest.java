package com.example.communicationservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Demande d'envoi d'une notification de paiement à un client (réservée aux administrateurs).
 * {@code clientId} est le {@code sub} Keycloak du client ; {@code amount} le montant dû ;
 * {@code message} un libellé libre facultatif. Solution d'attente : aucun suivi de paiement n'est
 * effectué (futur service de facturation).
 */
public record CreatePaymentNotificationRequest(
        @NotBlank String clientId,
        @NotNull @Positive BigDecimal amount,
        @Size(max = 1000) String message
) {
}
