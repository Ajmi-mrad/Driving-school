package com.example.financeservice.web.dto;

import com.example.financeservice.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Corps de {@code PUT /api/payments/{id}} : modification d'un paiement existant. */
public record UpdatePaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull PaymentMethod method,
        @Size(max = 255) String reference
) {
}
