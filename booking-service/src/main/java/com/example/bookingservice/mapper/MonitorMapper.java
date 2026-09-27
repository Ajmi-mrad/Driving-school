package com.example.bookingservice.mapper;

import com.example.bookingservice.domain.MonitorAvailability;
import com.example.bookingservice.domain.MonitorTimeOff;
import com.example.bookingservice.web.dto.AvailabilityRuleResponse;
import com.example.bookingservice.web.dto.TimeOffResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MonitorMapper {

    AvailabilityRuleResponse toResponse(MonitorAvailability availability);

    TimeOffResponse toResponse(MonitorTimeOff timeOff);
}