package com.example.financeservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateEnrollmentRequest(
        @NotBlank String clientId,
        @NotNull UUID forfaitId
) {
}
