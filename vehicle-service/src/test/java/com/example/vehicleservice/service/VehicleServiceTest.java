package com.example.vehicleservice.service;

import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.GearboxType;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.domain.VehicleStatus;
import com.example.vehicleservice.exception.DuplicateRegistrationException;
import com.example.vehicleservice.exception.VehicleNotFoundException;
import com.example.vehicleservice.mapper.VehicleMapper;
import com.example.vehicleservice.repository.VehicleRepository;
import com.example.vehicleservice.web.dto.CreateVehicleRequest;
import com.example.vehicleservice.web.dto.UpdateVehicleRequest;
import com.example.vehicleservice.web.dto.VehicleResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    VehicleRepository vehicleRepository;
    @Mock
    VehicleMapper vehicleMapper;
    @InjectMocks
    VehicleService vehicleService;

    private CreateVehicleRequest createRequest() {
        return new CreateVehicleRequest("Renault", "Clio", "123-TUN-456",
                GearboxType.MANUAL, FuelType.DIESEL, 2020, 12000, null, null);
    }

    private VehicleResponse dummyResponse() {
        return new VehicleResponse(UUID.randomUUID(), "Renault", "Clio", "123-TUN-456",
                GearboxType.MANUAL, FuelType.DIESEL, VehicleStatus.AVAILABLE, 2020, 12000,
                null, null, Instant.now(), "system", Instant.now(), "system");
    }

    @Test
    void create_persistsWhenRegistrationFree() {
        CreateVehicleRequest request = createRequest();
        when(vehicleRepository.existsByRegistrationNumber("123-TUN-456")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleMapper.toResponse(any(Vehicle.class))).thenReturn(dummyResponse());

        VehicleResponse result = vehicleService.create(request);

        assertThat(result).isNotNull();
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void create_duplicateRegistration_throwsAndSkipsSave() {
        CreateVehicleRequest request = createRequest();
        when(vehicleRepository.existsByRegistrationNumber("123-TUN-456")).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.create(request))
                .isInstanceOf(DuplicateRegistrationException.class);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void get_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.get(id))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void update_appliesNonNullFieldsAndFlushes() {
        UUID id = UUID.randomUUID();
        Vehicle existing = new Vehicle();
        existing.setBrand("Renault");
        existing.setModel("Clio");
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleMapper.toResponse(any(Vehicle.class))).thenReturn(dummyResponse());

        UpdateVehicleRequest request = new UpdateVehicleRequest("Peugeot", null, null, null, null, 99000, null, null);
        vehicleService.update(id, request);

        verify(vehicleRepository).saveAndFlush(existing);
        assertThat(existing.getBrand()).isEqualTo("Peugeot");
        assertThat(existing.getModel()).isEqualTo("Clio");
        assertThat(existing.getMileage()).isEqualTo(99000);
    }

    @Test
    void updateStatus_setsStatus() {
        UUID id = UUID.randomUUID();
        Vehicle existing = new Vehicle();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleMapper.toResponse(any(Vehicle.class))).thenReturn(dummyResponse());

        vehicleService.updateStatus(id, VehicleStatus.IN_USE);

        assertThat(existing.getStatus()).isEqualTo(VehicleStatus.IN_USE);
    }

    @Test
    void retire_setsOutOfService() {
        UUID id = UUID.randomUUID();
        Vehicle existing = new Vehicle();
        existing.setStatus(VehicleStatus.AVAILABLE);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(existing));

        vehicleService.retire(id);

        assertThat(existing.getStatus()).isEqualTo(VehicleStatus.OUT_OF_SERVICE);
        verify(vehicleRepository).save(existing);
    }
}