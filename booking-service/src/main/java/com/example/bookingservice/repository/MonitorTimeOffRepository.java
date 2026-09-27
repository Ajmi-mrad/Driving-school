package com.example.bookingservice.repository;

import com.example.bookingservice.domain.MonitorTimeOff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MonitorTimeOffRepository extends JpaRepository<MonitorTimeOff, UUID> {

    List<MonitorTimeOff> findByMonitorIdOrderByStartTimeAsc(String monitorId);

    /**
     * Absences d'un moniteur qui chevauchent l'intervalle {@code [from, to)} : {@code start < to ET
     * end > from}. Utilisé pour retrancher les absences lors du calcul des créneaux réservables.
     */
    List<MonitorTimeOff> findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(
            String monitorId, Instant to, Instant from);
}
