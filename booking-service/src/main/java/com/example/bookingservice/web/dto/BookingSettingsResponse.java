package com.example.bookingservice.web.dto;

public record BookingSettingsResponse(
        boolean autoValidationEnabled,
        int cancellationNoticeHours
) {
}