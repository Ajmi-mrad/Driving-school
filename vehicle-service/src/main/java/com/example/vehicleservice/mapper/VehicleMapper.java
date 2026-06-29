package com.example.vehicleservice.mapper;

import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.web.dto.VehicleResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VehicleMapper {

    VehicleResponse toResponse(Vehicle vehicle);
}