package com.example.financeservice.web.dto;

import com.example.financeservice.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID enrollmentId,
        String clientId,
        BigDecimal amount,
        PaymentMethod method,
        String reference,
        Instant paidAt
) {
}
