package com.example.bookingservice.repository;

import com.example.bookingservice.domain.MonitorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

public interface MonitorAvailabilityRepository extends JpaRepository<MonitorAvailability, UUID> {

    /** Règles hebdomadaires d'un moniteur, triées pour un affichage stable (jour puis heure). */
    List<MonitorAvailability> findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(String monitorId);

    /** Remplacement en bloc des règles d'un moniteur (PUT idempotent). */
    @Modifying
    void deleteByMonitorId(String monitorId);
}
