package com.example.bookingservice.web.dto;

import com.example.bookingservice.domain.ExamStatus;
import com.example.bookingservice.domain.ExamType;

import java.time.Instant;
import java.util.UUID;

public record ExamResponse(
        UUID id,
        ExamType type,
        String clientId,
        String monitorId,
        UUID vehicleId,
        Instant scheduledAt,
        String location,
        ExamStatus status,
        int attemptNumber,
        String resultNote,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {
}
