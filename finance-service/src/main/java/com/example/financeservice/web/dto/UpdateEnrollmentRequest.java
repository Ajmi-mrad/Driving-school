package com.example.financeservice.web.dto;

import com.example.financeservice.domain.EnrollmentStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Corps de {@code PUT /api/enrollments/{id}} : changement de forfait et/ou de statut. */
public record UpdateEnrollmentRequest(
        @NotNull UUID forfaitId,
        @NotNull EnrollmentStatus status
) {
}
