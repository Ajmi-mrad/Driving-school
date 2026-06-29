package com.example.bookingservice.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateBookingSettingsRequest(
        @NotNull Boolean autoValidationEnabled,
        @NotNull @PositiveOrZero Integer cancellationNoticeHours
) {
}