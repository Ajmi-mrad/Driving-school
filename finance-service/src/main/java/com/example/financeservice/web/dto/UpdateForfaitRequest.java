package com.example.financeservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateForfaitRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @PositiveOrZero int drivingHours,
        @PositiveOrZero int codeSessions,
        @NotNull @PositiveOrZero BigDecimal price,
        boolean active
) {
}
