package com.example.vehicleservice.service;

import com.example.vehicleservice.domain.MaintenanceRecord;
import com.example.vehicleservice.domain.MaintenanceType;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.exception.VehicleNotFoundException;
import com.example.vehicleservice.mapper.MaintenanceMapper;
import com.example.vehicleservice.repository.MaintenanceRecordRepository;
import com.example.vehicleservice.repository.VehicleRepository;
import com.example.vehicleservice.web.dto.CreateMaintenanceRequest;
import com.example.vehicleservice.web.dto.MaintenanceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock
    VehicleRepository vehicleRepository;
    @Mock
    MaintenanceRecordRepository maintenanceRepository;
    @Mock
    MaintenanceMapper maintenanceMapper;
    @InjectMocks
    MaintenanceService maintenanceService;

    @Test
    void addToVehicle_persistsWhenVehicleExists() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(new Vehicle()));
        when(maintenanceRepository.save(any(MaintenanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(maintenanceMapper.toResponse(any(MaintenanceRecord.class))).thenReturn(
                new MaintenanceResponse(UUID.randomUUID(), vehicleId, MaintenanceType.REVISION,
                        LocalDate.now(), new BigDecimal("100.00"), "ok",
                        Instant.now(), "system", Instant.now(), "system"));

        CreateMaintenanceRequest request = new CreateMaintenanceRequest(
                MaintenanceType.REVISION, LocalDate.now(), new BigDecimal("100.00"), "ok");
        MaintenanceResponse result = maintenanceService.addToVehicle(vehicleId, request);

        assertThat(result).isNotNull();
        verify(maintenanceRepository).save(any(MaintenanceRecord.class));
    }

    @Test
    void addToVehicle_vehicleMissing_throws() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        CreateMaintenanceRequest request = new CreateMaintenanceRequest(
                MaintenanceType.REVISION, LocalDate.now(), null, null);
        assertThatThrownBy(() -> maintenanceService.addToVehicle(vehicleId, request))
                .isInstanceOf(VehicleNotFoundException.class);
        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void listForVehicle_vehicleMissing_throws() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.existsById(vehicleId)).thenReturn(false);

        assertThatThrownBy(() -> maintenanceService.listForVehicle(vehicleId))
                .isInstanceOf(VehicleNotFoundException.class);
    }
}