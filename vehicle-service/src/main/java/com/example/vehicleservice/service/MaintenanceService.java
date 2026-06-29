package com.example.vehicleservice.service;

import com.example.vehicleservice.domain.MaintenanceRecord;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.exception.VehicleNotFoundException;
import com.example.vehicleservice.mapper.MaintenanceMapper;
import com.example.vehicleservice.repository.MaintenanceRecordRepository;
import com.example.vehicleservice.repository.VehicleRepository;
import com.example.vehicleservice.web.dto.CreateMaintenanceRequest;
import com.example.vehicleservice.web.dto.MaintenanceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Suivi des opérations d'entretien rattachées aux véhicules. */
@Service
public class MaintenanceService {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final MaintenanceMapper maintenanceMapper;

    public MaintenanceService(VehicleRepository vehicleRepository,
                              MaintenanceRecordRepository maintenanceRepository,
                              MaintenanceMapper maintenanceMapper) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.maintenanceMapper = maintenanceMapper;
    }

    @Transactional
    public MaintenanceResponse addToVehicle(UUID vehicleId, CreateMaintenanceRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        MaintenanceRecord record = new MaintenanceRecord();
        record.setVehicle(vehicle);
        record.setType(request.type());
        record.setPerformedAt(request.performedAt());
        record.setCost(request.cost());
        record.setDescription(request.description());
        return maintenanceMapper.toResponse(maintenanceRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceResponse> listForVehicle(UUID vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new VehicleNotFoundException(vehicleId);
        }
        return maintenanceRepository.findByVehicleIdOrderByPerformedAtDesc(vehicleId)
                .stream().map(maintenanceMapper::toResponse).toList();
    }
}