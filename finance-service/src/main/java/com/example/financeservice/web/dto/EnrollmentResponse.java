package com.example.financeservice.web.dto;

import com.example.financeservice.domain.EnrollmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        String clientId,
        UUID forfaitId,
        EnrollmentStatus status,
        BigDecimal totalPrice,
        BigDecimal amountPaid,
        BigDecimal outstanding,
        int remainingDrivingHours,
        int remainingCodeSessions,
        Instant enrolledAt
) {
}
