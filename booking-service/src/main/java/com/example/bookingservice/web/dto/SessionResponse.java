package com.example.bookingservice.web.dto;

import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        SessionType type,
        String clientId,
        String monitorId,
        UUID vehicleId,
        Instant startTime,
        Instant endTime,
        SessionStatus status,
        String notes,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {
}