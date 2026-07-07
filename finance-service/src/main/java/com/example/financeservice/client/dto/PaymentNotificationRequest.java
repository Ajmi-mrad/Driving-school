package com.example.financeservice.client.dto;

import java.math.BigDecimal;

/**
 * Corps attendu par le communication-service ({@code POST /api/notifications/payment}) pour émettre
 * un rappel de paiement (relance) à un client.
 */
public record PaymentNotificationRequest(
        String clientId,
        BigDecimal amount,
        String message
) {
}
