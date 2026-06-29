package com.example.vehicleservice.mapper;

import com.example.vehicleservice.domain.MaintenanceRecord;
import com.example.vehicleservice.domain.MaintenanceType;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.web.dto.MaintenanceResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceMapperTest {

    private final MaintenanceMapper mapper = new MaintenanceMapperImpl();

    @Test
    void mapsRecordAndFlattensVehicleId() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        MaintenanceRecord record = new MaintenanceRecord();
        record.setId(UUID.randomUUID());
        record.setVehicle(vehicle);
        record.setType(MaintenanceType.TECHNICAL_INSPECTION);
        record.setPerformedAt(LocalDate.parse("2026-03-15"));
        record.setCost(new BigDecimal("80.00"));
        record.setDescription("Contrôle technique");

        MaintenanceResponse response = mapper.toResponse(record);

        assertThat(response.vehicleId()).isEqualTo(vehicleId);
        assertThat(response.type()).isEqualTo(MaintenanceType.TECHNICAL_INSPECTION);
        assertThat(response.performedAt()).isEqualTo(LocalDate.parse("2026-03-15"));
        assertThat(response.cost()).isEqualByComparingTo("80.00");
        assertThat(response.description()).isEqualTo("Contrôle technique");
    }
}