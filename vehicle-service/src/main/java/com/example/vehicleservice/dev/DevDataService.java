package com.example.vehicleservice.dev;

import com.example.vehicleservice.dev.dto.SeedResult;
import com.example.vehicleservice.dev.dto.SeedVehiclesResponse;
import com.example.vehicleservice.dev.dto.VehicleExport;
import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.GearboxType;
import com.example.vehicleservice.domain.MaintenanceRecord;
import com.example.vehicleservice.domain.MaintenanceType;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.domain.VehicleStatus;
import com.example.vehicleservice.mapper.MaintenanceMapper;
import com.example.vehicleservice.mapper.VehicleMapper;
import com.example.vehicleservice.repository.MaintenanceRecordRepository;
import com.example.vehicleservice.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outil de développement (activé par {@code app.dev-tools.enabled=true}) : peuplement et remise à
 * zéro du parc automobile. Jeu de données aligné sur le mock frontend (mock/db.ts).
 */
@Service
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevDataService {

    private static final Logger log = LoggerFactory.getLogger(DevDataService.class);

    private record SeedVehicle(String key, String brand, String model, String registration,
                               GearboxType gearbox, FuelType fuel, VehicleStatus status,
                               int year, int mileage, long insuranceInDays, long inspectionInDays) {
    }

    private record SeedMaintenance(String vehicleKey, MaintenanceType type, String description,
                                   BigDecimal cost, long performedDaysAgo) {
    }

    private static final List<SeedVehicle> SEED_VEHICLES = List.of(
            new SeedVehicle("v-1", "Renault", "Clio V", "AB-123-CD", GearboxType.MANUAL, FuelType.DIESEL,
                    VehicleStatus.AVAILABLE, 2021, 68420, 120, 40),
            new SeedVehicle("v-2", "Peugeot", "208", "EF-456-GH", GearboxType.AUTOMATIC, FuelType.PETROL,
                    VehicleStatus.IN_USE, 2022, 41230, 200, 15),
            new SeedVehicle("v-3", "Citroen", "C3", "IJ-789-KL", GearboxType.MANUAL, FuelType.DIESEL,
                    VehicleStatus.MAINTENANCE, 2019, 112800, 60, -5),
            new SeedVehicle("v-4", "Volkswagen", "Golf", "MN-012-OP", GearboxType.AUTOMATIC, FuelType.ELECTRIC,
                    VehicleStatus.AVAILABLE, 2023, 15600, 300, 500));

    private static final List<SeedMaintenance> SEED_MAINTENANCE = List.of(
            new SeedMaintenance("v-3", MaintenanceType.REPAIR, "Remplacement plaquettes de frein avant",
                    new BigDecimal("180.00"), 3),
            new SeedMaintenance("v-1", MaintenanceType.REVISION, "Revision des 60 000 km",
                    new BigDecimal("320.00"), 45),
            new SeedMaintenance("v-2", MaintenanceType.TECHNICAL_INSPECTION, "Controle technique periodique",
                    new BigDecimal("78.00"), 90));

    private final VehicleRepository vehicleRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final VehicleMapper vehicleMapper;
    private final MaintenanceMapper maintenanceMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public DevDataService(VehicleRepository vehicleRepository, MaintenanceRecordRepository maintenanceRepository,
                          VehicleMapper vehicleMapper, MaintenanceMapper maintenanceMapper) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.vehicleMapper = vehicleMapper;
        this.maintenanceMapper = maintenanceMapper;
    }

    @Transactional
    public SeedVehiclesResponse seed() {
        Map<String, Vehicle> byKey = new LinkedHashMap<>();
        Map<String, String> idMap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        int created = 0;
        int skipped = 0;
        for (SeedVehicle sv : SEED_VEHICLES) {
            Vehicle vehicle = vehicleRepository.findByRegistrationNumber(sv.registration()).orElse(null);
            if (vehicle != null) {
                byKey.put(sv.key(), vehicle);
                idMap.put(sv.key(), vehicle.getId().toString());
                skipped++;
                continue;
            }
            vehicle = new Vehicle();
            vehicle.setBrand(sv.brand());
            vehicle.setModel(sv.model());
            vehicle.setRegistrationNumber(sv.registration());
            vehicle.setGearboxType(sv.gearbox());
            vehicle.setFuelType(sv.fuel());
            vehicle.setStatus(sv.status());
            vehicle.setManufactureYear(sv.year());
            vehicle.setMileage(sv.mileage());
            vehicle.setInsuranceExpiry(today.plusDays(sv.insuranceInDays()));
            vehicle.setTechnicalInspectionExpiry(today.plusDays(sv.inspectionInDays()));
            Vehicle saved = vehicleRepository.save(vehicle);
            byKey.put(sv.key(), saved);
            idMap.put(sv.key(), saved.getId().toString());
            created++;
        }

        // Entretien : ne seed qu'une fois (table vide) pour rester idempotent.
        if (maintenanceRepository.count() == 0) {
            for (SeedMaintenance sm : SEED_MAINTENANCE) {
                Vehicle vehicle = byKey.get(sm.vehicleKey());
                if (vehicle == null) {
                    continue;
                }
                MaintenanceRecord record = new MaintenanceRecord();
                record.setVehicle(vehicle);
                record.setType(sm.type());
                record.setDescription(sm.description());
                record.setCost(sm.cost());
                record.setPerformedAt(today.minusDays(sm.performedDaysAgo()));
                maintenanceRepository.save(record);
            }
        }
        log.info("Seed vehicle: {} créés, {} ignorés", created, skipped);
        return new SeedVehiclesResponse(idMap, new SeedResult(created, skipped));
    }

    @Transactional
    public void reset() {
        entityManager.createNativeQuery(
                "TRUNCATE TABLE maintenance_records, vehicles RESTART IDENTITY CASCADE").executeUpdate();
        log.info("Reset vehicle: parc et entretien vidés");
    }

    @Transactional(readOnly = true)
    public VehicleExport export() {
        return new VehicleExport(
                vehicleRepository.findAll().stream().map(vehicleMapper::toResponse).toList(),
                maintenanceRepository.findAll().stream().map(maintenanceMapper::toResponse).toList());
    }
}
