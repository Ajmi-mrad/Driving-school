package com.example.vehicleservice;

import com.example.vehicleservice.config.AuditConfig;
import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.GearboxType;
import com.example.vehicleservice.domain.MaintenanceRecord;
import com.example.vehicleservice.domain.MaintenanceType;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.domain.VehicleStatus;
import com.example.vehicleservice.repository.MaintenanceRecordRepository;
import com.example.vehicleservice.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie l'étape 2 : Flyway crée le schéma, Hibernate le valide (ddl-auto=validate), et les
 * colonnes d'audit se remplissent automatiquement à la persistance.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "eureka.client.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, AuditConfig.class})
class VehicleAuditIT {

    @Autowired
    VehicleRepository vehicleRepository;
    @Autowired
    MaintenanceRecordRepository maintenanceRepository;

    @Test
    void persistsVehicleAndMaintenanceWithAuditColumns() {
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        vehicle.setRegistrationNumber("123-TUN-" + UUID.randomUUID());
        vehicle.setGearboxType(GearboxType.MANUAL);
        vehicle.setFuelType(FuelType.DIESEL);

        Vehicle savedVehicle = vehicleRepository.saveAndFlush(vehicle);

        assertThat(savedVehicle.getId()).isNotNull();
        assertThat(savedVehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(savedVehicle.getCreatedAt()).isNotNull();
        assertThat(savedVehicle.getCreatedBy()).isEqualTo("system");

        MaintenanceRecord record = new MaintenanceRecord();
        record.setVehicle(savedVehicle);
        record.setType(MaintenanceType.REVISION);
        record.setPerformedAt(LocalDate.now());
        record.setCost(new BigDecimal("120.50"));
        record.setDescription("Révision des 10 000 km");

        MaintenanceRecord savedRecord = maintenanceRepository.saveAndFlush(record);

        assertThat(savedRecord.getId()).isNotNull();
        assertThat(savedRecord.getCreatedAt()).isNotNull();
        assertThat(savedRecord.getCreatedBy()).isEqualTo("system");
        assertThat(maintenanceRepository.findByVehicleIdOrderByPerformedAtDesc(savedVehicle.getId()))
                .hasSize(1);
    }
}