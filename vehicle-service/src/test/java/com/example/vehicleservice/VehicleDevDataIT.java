package com.example.vehicleservice;

import com.example.vehicleservice.dev.DevDataService;
import com.example.vehicleservice.dev.dto.SeedVehiclesResponse;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.repository.MaintenanceRecordRepository;
import com.example.vehicleservice.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration de l'outil de dev vehicle (seed / reset) sur une vraie base Postgres.
 */
@SpringBootTest(properties = "app.dev-tools.enabled=true")
@Import(TestcontainersConfiguration.class)
class VehicleDevDataIT {

    @Autowired
    private DevDataService devDataService;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private MaintenanceRecordRepository maintenanceRepository;

    @BeforeEach
    void clean() {
        devDataService.reset();
    }

    @Test
    void seed_createsVehiclesAndMaintenance_withIdMap() {
        SeedVehiclesResponse res = devDataService.seed();

        assertThat(res.result().created()).isEqualTo(4);
        assertThat(res.vehicles()).containsKeys("v-1", "v-2", "v-3", "v-4");
        assertThat(vehicleRepository.count()).isEqualTo(4);
        assertThat(maintenanceRepository.count()).isEqualTo(3);
        // Every id in the map resolves to a persisted vehicle.
        res.vehicles().values()
                .forEach(id -> assertThat(vehicleRepository.findById(UUID.fromString(id))).isPresent());
    }

    @Test
    void seed_isIdempotent_byRegistration() {
        devDataService.seed();
        SeedVehiclesResponse second = devDataService.seed();

        assertThat(second.result().created()).isZero();
        assertThat(second.result().skipped()).isEqualTo(4);
        assertThat(vehicleRepository.count()).isEqualTo(4); // not doubled
        assertThat(maintenanceRepository.count()).isEqualTo(3);
    }

    @Test
    void seed_inspectionExpiry_isRelativeToToday() {
        devDataService.seed();

        Vehicle v3 = vehicleRepository.findByRegistrationNumber("IJ-789-KL").orElseThrow();
        assertThat(v3.getTechnicalInspectionExpiry()).isEqualTo(LocalDate.now().minusDays(5));
        Vehicle v4 = vehicleRepository.findByRegistrationNumber("MN-012-OP").orElseThrow();
        assertThat(v4.getTechnicalInspectionExpiry()).isEqualTo(LocalDate.now().plusDays(500));
    }

    @Test
    void reset_truncatesVehiclesAndMaintenance() {
        devDataService.seed();
        devDataService.reset();

        assertThat(vehicleRepository.count()).isZero();
        assertThat(maintenanceRepository.count()).isZero();
    }
}
