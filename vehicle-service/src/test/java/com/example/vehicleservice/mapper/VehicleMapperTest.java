package com.example.vehicleservice.mapper;

import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.GearboxType;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.domain.VehicleStatus;
import com.example.vehicleservice.web.dto.VehicleResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleMapperTest {

    private final VehicleMapper mapper = new VehicleMapperImpl();

    @Test
    void mapsAllFieldsToResponse() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        vehicle.setRegistrationNumber("123-TUN-456");
        vehicle.setGearboxType(GearboxType.MANUAL);
        vehicle.setFuelType(FuelType.DIESEL);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle.setManufactureYear(2020);
        vehicle.setMileage(12000);
        vehicle.setInsuranceExpiry(LocalDate.parse("2026-12-31"));
        vehicle.setTechnicalInspectionExpiry(LocalDate.parse("2026-06-30"));
        vehicle.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        vehicle.setCreatedBy("system");

        VehicleResponse response = mapper.toResponse(vehicle);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.brand()).isEqualTo("Renault");
        assertThat(response.registrationNumber()).isEqualTo("123-TUN-456");
        assertThat(response.gearboxType()).isEqualTo(GearboxType.MANUAL);
        assertThat(response.fuelType()).isEqualTo(FuelType.DIESEL);
        assertThat(response.status()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(response.manufactureYear()).isEqualTo(2020);
        assertThat(response.insuranceExpiry()).isEqualTo(LocalDate.parse("2026-12-31"));
        assertThat(response.createdBy()).isEqualTo("system");
    }
}