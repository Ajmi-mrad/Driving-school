package com.example.vehicleservice.repository;

import com.example.vehicleservice.domain.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, UUID> {

    List<MaintenanceRecord> findByVehicleIdOrderByPerformedAtDesc(UUID vehicleId);
}