package com.example.financeservice.web.dto;

import com.example.financeservice.domain.InvoiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID enrollmentId,
        String clientId,
        String number,
        InvoiceType type,
        BigDecimal amount,
        Instant issuedAt
) {
}
