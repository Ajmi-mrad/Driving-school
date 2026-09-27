package com.example.bookingservice.web.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityRuleResponse(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
}