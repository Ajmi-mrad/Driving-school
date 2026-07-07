package com.example.financeservice.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ForfaitResponse(
        UUID id,
        String name,
        String description,
        int drivingHours,
        int codeSessions,
        BigDecimal price,
        boolean active
) {
}
