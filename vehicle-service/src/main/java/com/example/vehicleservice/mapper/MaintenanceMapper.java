package com.example.vehicleservice.mapper;

import com.example.vehicleservice.domain.MaintenanceRecord;
import com.example.vehicleservice.web.dto.MaintenanceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MaintenanceMapper {

    @Mapping(target = "vehicleId", source = "vehicle.id")
    MaintenanceResponse toResponse(MaintenanceRecord record);
}